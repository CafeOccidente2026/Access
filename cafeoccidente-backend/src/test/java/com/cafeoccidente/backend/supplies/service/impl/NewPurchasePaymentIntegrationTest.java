package com.cafeoccidente.backend.supplies.service.impl;

import static com.cafeoccidente.backend.dataexport.repository.DataExportRepository.PURCHASE_COLUMNS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.common.security.SecurityUser;
import com.cafeoccidente.backend.dataexport.repository.DataExportRepository;
import com.cafeoccidente.backend.dataexport.repository.ExportRow;
import com.cafeoccidente.backend.inventory.service.InventoryMovementService;
import com.cafeoccidente.backend.purchases.drycoffee.dto.DryCoffeePurchaseRequest;
import com.cafeoccidente.backend.purchases.drycoffee.dto.DryCoffeePurchaseResponse;
import com.cafeoccidente.backend.purchases.drycoffee.dto.SpecialInfoResponse;
import com.cafeoccidente.backend.purchases.drycoffee.service.DryCoffeePurchaseService;
import com.cafeoccidente.backend.purchases.shared.dto.PurchasePaymentRequest;
import com.cafeoccidente.backend.users.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * Una compra NUEVA de Cafe Seco pagada en efectivo + cheque (como las 122 mixtas reales de El Tambo)
 * tiene que aparecer igual que una migrada: dos filas en Caja (ActualizaCaja / ActualizaCh), el cheque
 * en Suministros (ActualizaSuministros), pendiente en Relacion Cheques y con FPef/FPch en Exportar.
 * Transaccional: no deja nada. El inventario es mock porque se graba en su propia transaccion.
 */
@SpringBootTest
@Transactional
@SuppressWarnings("null")
class NewPurchasePaymentIntegrationTest {

    private static final String GROWER = "1086360394";

    @Autowired
    private DryCoffeePurchaseService purchases;
    @Autowired
    private SuppliesReportServiceImpl supplies;
    @Autowired
    private DataExportRepository export;
    @Autowired
    private UserRepository users;
    @Autowired
    private JdbcTemplate jdbc;
    @MockitoBean
    private InventoryMovementService inventory;

    private Long agencyId;

    @BeforeEach
    void signInAsElTambo() {
        var user = users.findByUsername("admin_eltambo");
        Assumptions.assumeTrue(user.isPresent(), "Sin usuario de El Tambo - corra la migracion primero.");
        SecurityUser principal = new SecurityUser(user.get());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        agencyId = principal.getAgencyId();
        Assumptions.assumeTrue(jdbc.queryForObject(
                "SELECT count(*) FROM grower WHERE id_number = ?", Long.class, GROWER) > 0, "Sin el caficultor de prueba.");
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    private DryCoffeePurchaseRequest request(PurchasePaymentRequest payment) {
        Long fundId = jdbc.queryForObject("SELECT id FROM fund WHERE code = 'RP'", Long.class);
        SpecialInfoResponse info = purchases.specialInfo(agencyId, fundId, "NESPRESSO - FTUSA");
        Integer invoice = jdbc.queryForObject("SELECT COALESCE(MAX(invoice_number), 0) + 1000 FROM dry_coffee_purchase", Integer.class);
        return new DryCoffeePurchaseRequest(agencyId, fundId, invoice, "NESPRESSO - FTUSA", GROWER, "PRUEBA", "PAGO",
                "C", "VEREDA", "3000000000", 5, new BigDecimal("1000"), BigDecimal.ZERO, new BigDecimal("200"),
                new BigDecimal("10"), new BigDecimal("190"), info.healthyUnitPrice(), info.bonus(), BigDecimal.ZERO,
                info.costs(), false, BigDecimal.ZERO, BigDecimal.ZERO, payment);
    }

    @Test
    void aNewMixedPurchaseShowsUpInEveryCashReportLikeAMigratedOne() {
        BigDecimal net = purchases.preview(request(null)).netToPay();
        BigDecimal check = new BigDecimal("1000000");
        DryCoffeePurchaseResponse saved = purchases.create(request(
                new PurchasePaymentRequest(net.subtract(check), check, BigDecimal.ZERO, BigDecimal.ZERO, "987654")));
        String invoice = String.valueOf(saved.invoiceNumber());
        LocalDate today = LocalDate.now();

        assertThat(saved.paymentMethod()).isEqualTo("MIXTO");
        assertThat(saved.checkNumber()).isEqualTo("987654");

        List<SuppliesReportServiceImpl.Line> cash = supplies.cashBook(agencyId, today, today).stream()
                .filter(l -> l.transactionId().equals(invoice)).toList();
        assertThat(cash).extracting(SuppliesReportServiceImpl.Line::paymentMethod).containsExactlyInAnyOrder("EFECTIVO", "CHEQUE");
        assertThat(cash).filteredOn(l -> l.paymentMethod().equals("CHEQUE"))
                .singleElement().satisfies(l -> {
                    assertThat(l.outflow()).isEqualByComparingTo(check);
                    assertThat(l.checkNumber()).isEqualTo(987654);
                });
        assertThat(cash).filteredOn(l -> l.paymentMethod().equals("EFECTIVO"))
                .singleElement().satisfies(l -> assertThat(l.outflow()).isEqualByComparingTo(net.subtract(check)));

        assertThat(supplies.supplyBook(agencyId, today, today)).filteredOn(l -> l.transactionId().equals(invoice))
                .singleElement().satisfies(l -> assertThat(l.outflow()).isEqualByComparingTo(check));

        assertThat(supplies.checkRelation(agencyId, null, null, today))
                .anySatisfy(r -> assertThat(r.transactionId()).isEqualTo(invoice));

        ExportRow row = export.purchases(agencyId, true).stream()
                .filter(r -> invoice.equals(String.valueOf(r.values().get(PURCHASE_COLUMNS.indexOf("Factura")))))
                .findFirst().orElseThrow();
        assertThat((BigDecimal) row.values().get(PURCHASE_COLUMNS.indexOf("FPef"))).isEqualByComparingTo(net.subtract(check));
        assertThat((BigDecimal) row.values().get(PURCHASE_COLUMNS.indexOf("FPch"))).isEqualByComparingTo(check);
        assertThat(row.values().get(PURCHASE_COLUMNS.indexOf("NumCheque"))).isEqualTo(987654);
    }

    @Test
    void anUnbalancedPaymentIsNotSaved() {
        BigDecimal net = purchases.preview(request(null)).netToPay();
        Long before = jdbc.queryForObject("SELECT count(*) FROM dry_coffee_purchase", Long.class);

        assertThatThrownBy(() -> purchases.create(request(
                new PurchasePaymentRequest(net.subtract(BigDecimal.ONE), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null))))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM dry_coffee_purchase", Long.class)).isEqualTo(before);
    }
}
