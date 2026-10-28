package com.cafeoccidente.backend.supplies.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.common.security.CurrentAgencyProvider;
import com.cafeoccidente.backend.common.security.SecurityUser;
import com.cafeoccidente.backend.inventory.service.InventoryMovementService;
import com.cafeoccidente.backend.purchases.drycoffee.dto.DryCoffeePurchaseRequest;
import com.cafeoccidente.backend.purchases.drycoffee.dto.DryCoffeePurchaseResponse;
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
    private UserRepository users;
    @Autowired
    private JdbcTemplate jdbc;
    @MockitoBean
    private InventoryMovementService inventory;

    private Long agencyId;

    @BeforeEach
    void signInAsElTambo() {
        var user = users.findByUsername("admin_eltambo");
        Assumptions.assumeTrue(user.isPresent(), "FALTA el usuario admin_eltambo: corra 'python scripts/migrate_eltambo.py' contra esta base.");
        SecurityUser principal = new SecurityUser(user.get());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        agencyId = CurrentAgencyProvider.workingAgency(user.get()).getId();
        Assumptions.assumeTrue(jdbc.queryForObject(
                "SELECT count(*) FROM terceros WHERE identificacion = ?", Long.class, GROWER) > 0, "FALTA el tercero " + GROWER + " (viene de asociados_migrar.csv): corra 'python scripts/migrate_eltambo.py' contra esta base.");
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    private DryCoffeePurchaseRequest request(PurchasePaymentRequest payment) {
        Long fundId = jdbc.queryForObject("SELECT id FROM fondos WHERE codigo = 'RP'", Long.class);
        // Precios, costos y datos del vendedor los pone el servidor (anuncio vigente y TERCEROS).
        return new DryCoffeePurchaseRequest(fundId, "RN", GROWER, null, null,
                5, new BigDecimal("1000"), BigDecimal.ZERO, new BigDecimal("200"),
                new BigDecimal("10"), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, payment);
    }

    @Test
    void aNewMixedPurchaseShowsUpInEveryCashReportLikeAMigratedOne() {
        BigDecimal net = purchases.preview(request(null)).netToPay();
        BigDecimal check = new BigDecimal("1000000");
        // HU-15: el numero lo pone el servidor, el mismo que el encabezado mostro como proximo.
        int shown = purchases.nextInvoiceNumber().invoiceNumber();
        DryCoffeePurchaseResponse saved = purchases.create(request(
                new PurchasePaymentRequest(net.subtract(check), check, BigDecimal.ZERO, BigDecimal.ZERO, "987654")));
        String invoice = String.valueOf(saved.invoiceNumber());
        LocalDate today = LocalDate.now();

        assertThat(saved.invoiceNumber()).isEqualTo(shown);
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

        var split = jdbc.queryForMap("SELECT s.efectivo, s.cheque, s.numero_cheque FROM formas_pago_compras s"
                + " JOIN compras_cafe_seco p ON s.origen = 'DRY' AND s.origen_id = p.id WHERE p.id = ?", saved.id());
        assertThat((BigDecimal) split.get("efectivo")).isEqualByComparingTo(net.subtract(check));
        assertThat((BigDecimal) split.get("cheque")).isEqualByComparingTo(check);
        assertThat(split.get("numero_cheque")).isEqualTo(987654);
    }

    @Test
    void anUnbalancedPaymentIsNotSaved() {
        BigDecimal net = purchases.preview(request(null)).netToPay();
        Long before = jdbc.queryForObject("SELECT count(*) FROM compras_cafe_seco", Long.class);

        assertThatThrownBy(() -> purchases.create(request(
                new PurchasePaymentRequest(net.subtract(BigDecimal.ONE), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null))))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM compras_cafe_seco", Long.class)).isEqualTo(before);
    }
}
