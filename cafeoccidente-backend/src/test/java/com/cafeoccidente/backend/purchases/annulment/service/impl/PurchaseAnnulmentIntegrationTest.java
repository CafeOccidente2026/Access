package com.cafeoccidente.backend.purchases.annulment.service.impl;

import static com.cafeoccidente.backend.dataexport.repository.DataExportRepository.PURCHASE_COLUMNS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.common.exception.ResourceNotFoundException;
import com.cafeoccidente.backend.common.security.SecurityUser;
import com.cafeoccidente.backend.dataexport.repository.DataExportRepository;
import com.cafeoccidente.backend.purchases.annulment.dto.AnnulmentCandidate;
import com.cafeoccidente.backend.purchases.annulment.repository.AnnulmentModule;
import com.cafeoccidente.backend.supplies.dto.SuppliesReportRow;
import com.cafeoccidente.backend.supplies.service.SuppliesReportService;
import com.cafeoccidente.backend.users.repository.UserRepository;
import com.cafeoccidente.backend.vendors.repository.VendorReadRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Anular Documento" contra compras reales de El Tambo, como la macro AnularFactura: estado ANULADA,
 * los 29 campos en 0, sin copias en Inventario/Caja/Suministros y exportada con Forma_de_Pago =
 * ANULADA. Transaccional: no deja nada.
 */
@SpringBootTest
@Transactional
@SuppressWarnings("null")
class PurchaseAnnulmentIntegrationTest {

    @Autowired
    private PurchaseAnnulmentServiceImpl service;
    @Autowired
    private SuppliesReportService supplies;
    @Autowired
    private DataExportRepository export;
    @Autowired
    private VendorReadRepository vendors;
    @Autowired
    private UserRepository users;
    @Autowired
    private JdbcTemplate jdbc;

    private Long agencyId;

    @BeforeEach
    void signInAsElTambo() {
        var user = users.findByUsername("admin_eltambo");
        Assumptions.assumeTrue(user.isPresent(), "Sin usuario de El Tambo - corra la migracion primero.");
        SecurityUser principal = new SecurityUser(user.get());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        agencyId = principal.getAgencyId();
        Assumptions.assumeTrue(jdbc.queryForObject("SELECT count(*) FROM dry_coffee_purchase WHERE agency_id = ?"
                + " AND status = 'ANULADA'", Long.class, agencyId) > 0, "Sin las anuladas de El Tambo migradas.");
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    /** Una compra real de El Tambo, valida, todavia sin exportar y con pago mixto (efectivo + cheque). */
    private Map<String, Object> pendingMixedPurchase() {
        return jdbc.queryForMap("SELECT p.id, p.invoice_number, p.total_stored_weight, p.base_price_load,"
                + " p.product_code_id, p.special_type, p.purchase_date, p.net_kg FROM dry_coffee_purchase p"
                + " JOIN purchase_payment_split s ON s.source = 'DRY' AND s.source_id = p.id"
                + " WHERE p.agency_id = ? AND p.status = 'VALIDA' AND NOT p.exported AND s.check_amount > 0"
                + " ORDER BY p.id LIMIT 1", agencyId);
    }

    private void inventoryEntry(Map<String, Object> p, BigDecimal remainingKg) {
        jdbc.update("INSERT INTO inventory_movement (purchase_module, purchase_id, agency_id, product_code_id,"
                        + " special_type, invoice_number, purchase_date, sacos, gross_kg, net_kg, remaining_kg,"
                        + " healthy_percentage, inventory_value, created_at, exported)"
                        + " VALUES ('DRY_COFFEE', ?, ?, ?, ?, ?, ?, 1, ?, ?, ?, 90, 1, now(), false)",
                p.get("id"), agencyId, p.get("product_code_id"), p.get("special_type"), p.get("invoice_number"),
                p.get("purchase_date"), p.get("net_kg"), p.get("net_kg"), remainingKg);
    }

    @Test
    void annulsLikeAnularFactura() {
        Map<String, Object> p = pendingMixedPurchase();
        Long id = ((Number) p.get("id")).longValue();
        String invoice = String.valueOf(p.get("invoice_number"));
        inventoryEntry(p, (BigDecimal) p.get("net_kg"));

        AnnulmentCandidate annulled = service.annul(agencyId, AnnulmentModule.DRY, id);

        assertThat(annulled.status()).isEqualTo("ANULADA");
        assertThat(annulled.netToPay()).isZero();
        assertThat(annulled.netKg()).isZero();
        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM dry_coffee_purchase WHERE id = ?", id);
        for (String zero : List.of("bags_count", "gross_kg", "gross_value", "withholding", "healthy_percentage",
                "healthy_unit_price", "costs", "cooperative_discount", "associate_contribution")) {
            assertThat(((Number) row.get(zero)).doubleValue()).as(zero).isZero();
        }
        // AnularFactura no toca W_TotAlm ni Pr_Base_PC.
        assertThat((BigDecimal) row.get("total_stored_weight")).isEqualByComparingTo((BigDecimal) p.get("total_stored_weight"));
        assertThat((BigDecimal) row.get("base_price_load")).isEqualByComparingTo((BigDecimal) p.get("base_price_load"));
        assertThat(row.get("check_number")).isNull();
        assertThat(row.get("annulled_at")).isNotNull();
        assertThat(row.get("annulled_by_user_id")).isNotNull();
        assertThat(jdbc.queryForObject("SELECT cash_amount + check_amount + transfer_amount + card_amount"
                + " FROM purchase_payment_split WHERE source = 'DRY' AND source_id = ?", BigDecimal.class, id)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_movement WHERE purchase_module = 'DRY_COFFEE'"
                + " AND purchase_id = ?", Long.class, id)).isZero();

        java.time.LocalDate date = ((java.sql.Date) p.get("purchase_date")).toLocalDate();
        // Caja (Formas de Pago, todas las formas) y Suministros RP / LF ya no la tienen.
        assertThat(supplies.paymentMethods(agencyId, date, date, "*")).extracting(SuppliesReportRow::transactionId)
                .doesNotContain(invoice);
        for (String fund : List.of("RP", "LF")) {
            assertThat(supplies.supplies(agencyId, fund, date)).extracting(SuppliesReportRow::transactionId)
                    .doesNotContain(invoice);
        }

        var exported = export.purchases(agencyId, true).stream()
                .filter(r -> invoice.equals(String.valueOf(r.values().get(PURCHASE_COLUMNS.indexOf("Factura")))))
                .findFirst().orElseThrow();
        assertThat(exported.values().get(PURCHASE_COLUMNS.indexOf("Forma_de_Pago"))).isEqualTo("ANULADA");
        assertThat((BigDecimal) exported.values().get(PURCHASE_COLUMNS.indexOf("FPch"))).isZero();

        assertThat(vendors.beneficiary(agencyId, (String) row.get("id_number"), null, null))
                .filteredOn(b -> b.invoiceNumber().toString().equals(invoice))
                .singleElement().satisfies(b -> assertThat(b.netToPay()).isZero());
    }

    @Test
    void cannotAnnulWhatWasAlreadyExported() {
        Long id = jdbc.queryForObject("SELECT id FROM dry_coffee_purchase WHERE agency_id = ? AND status = 'VALIDA'"
                + " AND exported ORDER BY id LIMIT 1", Long.class, agencyId);
        assertThatThrownBy(() -> service.annul(agencyId, AnnulmentModule.DRY, id))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("IMPOSIBLE ANULAR EL DOCUMENTO SOPORTE PORQUE YA EXPORTO LA INFORMACION.");
    }

    @Test
    void cannotAnnulWhenItsInventoryAlreadyLeft() {
        Map<String, Object> p = pendingMixedPurchase();
        inventoryEntry(p, BigDecimal.ONE);
        assertThatThrownBy(() -> service.annul(agencyId, AnnulmentModule.DRY, ((Number) p.get("id")).longValue()))
                .isInstanceOf(BusinessRuleException.class).hasMessage("La compra ya tiene salidas de inventario");
    }

    @Test
    void cannotAnnulTwiceNorFromAnotherAgency() {
        Long annulled = jdbc.queryForObject("SELECT id FROM dry_coffee_purchase WHERE agency_id = ? AND status = 'ANULADA'"
                + " AND NOT exported ORDER BY id LIMIT 1", Long.class, agencyId);
        assertThatThrownBy(() -> service.annul(agencyId, AnnulmentModule.DRY, annulled))
                .isInstanceOf(BusinessRuleException.class).hasMessage("El documento soporte ya esta anulado");
        Long pending = ((Number) pendingMixedPurchase().get("id")).longValue();
        assertThatThrownBy(() -> service.annul(agencyId + 1000, AnnulmentModule.DRY, pending))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void findsTheInvoiceInEveryModuleOfTheAgency() {
        Map<String, Object> p = pendingMixedPurchase();
        assertThat(service.findByInvoice(agencyId, (Integer) p.get("invoice_number")))
                .singleElement().satisfies(c -> {
                    assertThat(c.module()).isEqualTo(AnnulmentModule.DRY);
                    assertThat(c.status()).isEqualTo("VALIDA");
                });
    }
}
