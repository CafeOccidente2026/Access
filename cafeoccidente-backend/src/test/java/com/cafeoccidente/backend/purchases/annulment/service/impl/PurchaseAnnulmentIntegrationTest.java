package com.cafeoccidente.backend.purchases.annulment.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.common.exception.ResourceNotFoundException;
import com.cafeoccidente.backend.common.security.CurrentAgencyProvider;
import com.cafeoccidente.backend.common.security.SecurityUser;
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
    private VendorReadRepository vendors;
    @Autowired
    private UserRepository users;
    @Autowired
    private JdbcTemplate jdbc;

    private Long agencyId;

    @BeforeEach
    void signInAsElTambo() {
        var user = users.findByUsername("admin_eltambo");
        Assumptions.assumeTrue(user.isPresent(), "FALTA el usuario admin_eltambo: corra 'python scripts/migrate_eltambo.py' contra esta base.");
        SecurityUser principal = new SecurityUser(user.get());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        agencyId = CurrentAgencyProvider.workingAgency(user.get()).getId();
        Assumptions.assumeTrue(jdbc.queryForObject("SELECT count(*) FROM compras_cafe_seco WHERE agencia_id = ?"
                + " AND estado = 'ANULADA'", Long.class, agencyId) > 0, "FALTAN las compras anuladas de El Tambo: corra 'python scripts/migrate_eltambo.py' contra esta base.");
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    /** Una compra real de El Tambo, valida, anulable y con pago mixto (efectivo + cheque). */
    private Map<String, Object> pendingMixedPurchase() {
        return jdbc.queryForMap("SELECT p.id, p.numero_factura, p.peso_tot_alm, p.pr_base_cps,"
                + " p.producto_id, p.especial, p.fecha_compra, p.kilos_netos FROM compras_cafe_seco p"
                + " JOIN formas_pago_compras s ON s.origen = 'DRY' AND s.origen_id = p.id"
                + " WHERE p.agencia_id = ? AND p.estado = 'VALIDA' AND NOT p.anulacion_bloqueada AND s.cheque > 0"
                + " ORDER BY p.id LIMIT 1", agencyId);
    }

    private void inventoryEntry(Map<String, Object> p, BigDecimal remainingKg) {
        jdbc.update("INSERT INTO inventarios (modulo_compra, compra_id, agencia_id, producto_id,"
                        + " especial, numero_factura, fecha_compra, sacos, kilos_brutos, kilos_netos, saldo_kilos,"
                        + " factor, vr_inventario, creado_en)"
                        + " VALUES ('DRY_COFFEE', ?, ?, ?, ?, ?, ?, 1, ?, ?, ?, 90, 1, now())",
                p.get("id"), agencyId, p.get("producto_id"), p.get("especial"), p.get("numero_factura"),
                p.get("fecha_compra"), p.get("kilos_netos"), p.get("kilos_netos"), remainingKg);
    }

    @Test
    void annulsLikeAnularFactura() {
        Map<String, Object> p = pendingMixedPurchase();
        Long id = ((Number) p.get("id")).longValue();
        String invoice = String.valueOf(p.get("numero_factura"));
        inventoryEntry(p, (BigDecimal) p.get("kilos_netos"));

        AnnulmentCandidate annulled = service.annul(agencyId, AnnulmentModule.DRY, id);

        assertThat(annulled.status()).isEqualTo("ANULADA");
        assertThat(annulled.netToPay()).isZero();
        assertThat(annulled.netKg()).isZero();
        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM compras_cafe_seco WHERE id = ?", id);
        for (String zero : List.of("sacos", "kilos_brutos", "vr_bruto", "retefuente", "factor",
                "pr_alm_sana", "costos", "descuento_cooperativo", "aporte_asociado")) {
            assertThat(((Number) row.get(zero)).doubleValue()).as(zero).isZero();
        }
        // AnularFactura no toca W_TotAlm ni Pr_Base_PC.
        assertThat((BigDecimal) row.get("peso_tot_alm")).isEqualByComparingTo((BigDecimal) p.get("peso_tot_alm"));
        assertThat((BigDecimal) row.get("pr_base_cps")).isEqualByComparingTo((BigDecimal) p.get("pr_base_cps"));
        assertThat(row.get("numero_cheque")).isNull();
        assertThat(row.get("anulado_en")).isNotNull();
        assertThat(row.get("anulado_por_usuario_id")).isNotNull();
        assertThat(jdbc.queryForObject("SELECT efectivo + cheque + transferencia + datafono"
                + " FROM formas_pago_compras WHERE origen = 'DRY' AND origen_id = ?", BigDecimal.class, id)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventarios WHERE modulo_compra = 'DRY_COFFEE'"
                + " AND compra_id = ?", Long.class, id)).isZero();

        java.time.LocalDate date = ((java.sql.Date) p.get("fecha_compra")).toLocalDate();
        // Caja (Formas de Pago, todas las formas) y Suministros RP / LF ya no la tienen.
        assertThat(supplies.paymentMethods(agencyId, date, date, "*")).extracting(SuppliesReportRow::transactionId)
                .doesNotContain(invoice);
        for (String fund : List.of("RP", "LF")) {
            assertThat(supplies.supplies(agencyId, fund, date)).extracting(SuppliesReportRow::transactionId)
                    .doesNotContain(invoice);
        }

        assertThat(vendors.beneficiary(agencyId, (String) row.get("cedula"), null, null))
                .filteredOn(b -> b.invoiceNumber().toString().equals(invoice))
                .singleElement().satisfies(b -> assertThat(b.netToPay()).isZero());
    }

    @Test
    void cannotAnnulAPurchaseLockedFromAccess() {
        Long id = jdbc.queryForObject("SELECT id FROM compras_cafe_seco WHERE agencia_id = ? AND estado = 'VALIDA'"
                + " AND anulacion_bloqueada ORDER BY id LIMIT 1", Long.class, agencyId);
        assertThatThrownBy(() -> service.annul(agencyId, AnnulmentModule.DRY, id))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("IMPOSIBLE ANULAR: ES UNA COMPRA CERRADA EN EL SISTEMA ANTERIOR.");
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
        Long annulled = jdbc.queryForObject("SELECT id FROM compras_cafe_seco WHERE agencia_id = ? AND estado = 'ANULADA'"
                + " AND NOT anulacion_bloqueada ORDER BY id LIMIT 1", Long.class, agencyId);
        assertThatThrownBy(() -> service.annul(agencyId, AnnulmentModule.DRY, annulled))
                .isInstanceOf(BusinessRuleException.class).hasMessage("El documento soporte ya esta anulado");
        Long pending = ((Number) pendingMixedPurchase().get("id")).longValue();
        assertThatThrownBy(() -> service.annul(agencyId + 1000, AnnulmentModule.DRY, pending))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void findsTheInvoiceInEveryModuleOfTheAgency() {
        Map<String, Object> p = pendingMixedPurchase();
        assertThat(service.findByInvoice(agencyId, (Integer) p.get("numero_factura")))
                .singleElement().satisfies(c -> {
                    assertThat(c.module()).isEqualTo(AnnulmentModule.DRY);
                    assertThat(c.status()).isEqualTo("VALIDA");
                });
    }
}
