package com.cafeoccidente.backend.purchases.annulment.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import com.cafeoccidente.backend.common.security.SecurityUtils;
import com.cafeoccidente.backend.purchases.annulment.repository.AnnulmentModule;
import com.cafeoccidente.backend.purchases.annulment.repository.PurchaseAnnulmentRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * 6f: la anulacion es una sola transaccion. Si falla despues de escribir todo, no queda nada a medias:
 * la compra sigue VALIDA y vuelven sus formas de pago, su inventario, su cuenta por cobrar y el saldo de
 * la compra anunciada. Sin @Transactional para ver el rollback real; deja la base como estaba.
 */
@SpringBootTest
@SuppressWarnings("null")
class PurchaseAnnulmentRollbackIntegrationTest {

    private static final long AGENCY = 1L;
    private static final int INVOICE = 990000003;
    private static final BigDecimal KG = new BigDecimal("100.00");
    private static final BigDecimal BALANCE = new BigDecimal("400.00");

    @Autowired
    private PurchaseAnnulmentServiceImpl service;
    @Autowired
    private JdbcTemplate jdbc;
    @MockitoSpyBean
    private PurchaseAnnulmentRepository repository;
    @MockitoBean
    private SecurityUtils securityUtils;

    private Long purchaseId;
    private Long futureId;

    @BeforeEach
    void committedPurchaseWithEverything() {
        Long userId = jdbc.queryForObject("SELECT min(id) FROM usuarios", Long.class);
        when(securityUtils.getCurrentUserId()).thenReturn(userId);
        futureId = jdbc.queryForObject("INSERT INTO compras_anunciadas (creado_en, agencia_id, numero_anuncio,"
                + " fecha_anuncio, cedula, nombres, tipo, direccion, especial, kilos, saldo, fecha_entrega, pr_alm_sana,"
                + " pr_alm_defec, bonificacion, costos, pr_inc_calidad, pr_base_cps, creado_por_usuario_id)"
                + " VALUES (now(), ?, '1', CURRENT_DATE, '1', 'PRUEBA', 'C', 'VEREDA', 'NESPRESSO - FTUSA', 500, ?, CURRENT_DATE,"
                + " 0, 0, 0, 0, 0, 0, ?) RETURNING id", Long.class, AGENCY, BALANCE, userId);
        purchaseId = jdbc.queryForObject("INSERT INTO compras_cafe_seco (fecha_compra, agencia_id, fondo_id, especial,"
                + " producto_id, numero_anuncio, fecha_anuncio, pr_base_cps, cedula, nombres, apellidos, tipo_vendedor,"
                + " direccion, celular, sacos, kilos_brutos, destare, kilos_netos, peso_tot_alm, porc_merma, peso_tot_pasilla,"
                + " porc_kg_pasilla, peso_alm_sana, factor, pr_alm_sana, pr_alm_defec, bonificacion, castigo, costos,"
                + " vr_kilo, vr_bruto, vr_inventario, aporte_asociado, descuento_cooperativo, retefuente,"
                + " descuento_financiero, otros_descuentos, neto_a_pagar, forma_pago, creado_por_usuario_id, creado_en,"
                + " numero_factura, compra_anunciada_id)"
                + " SELECT current_date, ?, (SELECT min(id) FROM fondos), 'RN', (SELECT min(id) FROM productos), '1',"
                + " current_date, 0, '1', 'PRUEBA', 'ANULACION', 'C', 'VEREDA', '3000000000', 1, ?, 0, ?, 0, 0, 0,"
                + " 0, 0, 0, 0, 0, 0, 0, 0, 0, 1000, 1000, 0, 0, 0, 0, 0, 1000, 'MIXTO', ?, now(), ?, ? RETURNING id",
                Long.class, AGENCY, KG, KG, userId, INVOICE, futureId);
        jdbc.update("INSERT INTO formas_pago_compras (origen, origen_id, efectivo, datafono) VALUES ('DRY', ?, 900, 100)",
                purchaseId);
        jdbc.update("INSERT INTO inventarios (modulo_compra, compra_id, agencia_id, producto_id, especial, numero_factura,"
                + " fecha_compra, kilos_netos, saldo_kilos, vr_inventario, creado_en) SELECT 'DRY_COFFEE', ?, ?,"
                + " (SELECT min(id) FROM productos), 'RN', ?, current_date, ?, ?, 1000, now()",
                purchaseId, AGENCY, INVOICE, KG, KG);
        jdbc.update("INSERT INTO cuentas_por_cobrar (agencia_id, modulo_compra, compra_id, numero_factura, cedula_vendedor,"
                + " cedula_fiel, valor) VALUES (?, 'DRY', ?, ?, '1', '2', 100)", AGENCY, purchaseId, INVOICE);
    }

    @AfterEach
    void removeIt() {
        jdbc.update("DELETE FROM cuentas_por_cobrar WHERE modulo_compra = 'DRY' AND compra_id = ?", purchaseId);
        jdbc.update("DELETE FROM inventarios WHERE modulo_compra = 'DRY_COFFEE' AND compra_id = ?", purchaseId);
        jdbc.update("DELETE FROM formas_pago_compras WHERE origen = 'DRY' AND origen_id = ?", purchaseId);
        jdbc.update("DELETE FROM compras_cafe_seco WHERE id = ?", purchaseId);
        jdbc.update("DELETE FROM compras_anunciadas WHERE id = ?", futureId);
    }

    @Test
    void ifTheLastStepFailsNothingOfTheAnnulmentRemains() {
        doAnswer(call -> {
            call.callRealMethod();
            throw new IllegalStateException("falla despues de escribir");
        }).when(repository).annul(any(), any(), any());

        // @Repository la traduce a una DataAccessException; lo que importa es que se revirtio todo.
        assertThatThrownBy(() -> service.annul(AGENCY, AnnulmentModule.DRY, purchaseId))
                .hasMessageContaining("falla despues de escribir");

        assertThat(jdbc.queryForMap("SELECT estado, kilos_netos, neto_a_pagar FROM compras_cafe_seco WHERE id = ?", purchaseId))
                .containsEntry("estado", "VALIDA")
                .satisfies(r -> assertThat((BigDecimal) r.get("kilos_netos")).isEqualByComparingTo(KG))
                .satisfies(r -> assertThat((BigDecimal) r.get("neto_a_pagar")).isEqualByComparingTo("1000"));
        assertThat(jdbc.queryForObject("SELECT efectivo + datafono FROM formas_pago_compras WHERE origen = 'DRY'"
                + " AND origen_id = ?", BigDecimal.class, purchaseId)).isEqualByComparingTo("1000");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventarios WHERE modulo_compra = 'DRY_COFFEE'"
                + " AND compra_id = ?", Long.class, purchaseId)).isOne();
        assertThat(jdbc.queryForObject("SELECT estado FROM cuentas_por_cobrar WHERE modulo_compra = 'DRY' AND compra_id = ?",
                String.class, purchaseId)).isEqualTo("PENDIENTE");
        assertThat(jdbc.queryForObject("SELECT saldo FROM compras_anunciadas WHERE id = ?", BigDecimal.class, futureId))
                .isEqualByComparingTo(BALANCE);
    }
}
