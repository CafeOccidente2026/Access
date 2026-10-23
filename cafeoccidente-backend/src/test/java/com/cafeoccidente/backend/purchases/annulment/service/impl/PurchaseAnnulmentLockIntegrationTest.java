package com.cafeoccidente.backend.purchases.annulment.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.common.security.SecurityUtils;
import com.cafeoccidente.backend.purchases.annulment.dto.AnnulmentCandidate;
import com.cafeoccidente.backend.purchases.annulment.repository.AnnulmentModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bloqueo de la anulacion (V5, anulacion_bloqueada) con compras propias sobre los datos semilla, asi
 * corre tambien en CI (PurchaseAnnulmentIntegrationTest necesita las compras de El Tambo). Transaccional.
 */
@SpringBootTest
@Transactional
@SuppressWarnings("null")
class PurchaseAnnulmentLockIntegrationTest {

    private static final int LOCKED = 990000001;
    private static final int OPEN = 990000002;

    @Autowired
    private PurchaseAnnulmentServiceImpl service;
    @Autowired
    private JdbcTemplate jdbc;
    @MockitoBean
    private SecurityUtils securityUtils;

    private Long agencyId;

    @BeforeEach
    void twoPurchases() {
        agencyId = jdbc.queryForObject("SELECT min(id) FROM agencias", Long.class);
        Long userId = jdbc.queryForObject("INSERT INTO usuarios (usuario, contrasena_hash, rol_id, agencia_id)"
                + " SELECT 'prueba_anulacion', 'x', min(id), ? FROM roles RETURNING id", Long.class, agencyId);
        when(securityUtils.getCurrentUserId()).thenReturn(userId);
        purchase(LOCKED, true, userId);
        purchase(OPEN, false, userId);
    }

    private void purchase(int invoice, boolean locked, Long userId) {
        jdbc.update("INSERT INTO compras_cafe_seco (fecha_compra, agencia_id, fondo_id, especial, producto_id,"
                + " numero_anuncio, fecha_anuncio, pr_base_cps, cedula, nombres, apellidos, tipo_vendedor, direccion,"
                + " celular, sacos, kilos_brutos, destare, kilos_netos, peso_tot_alm, porc_merma, peso_tot_pasilla,"
                + " porc_kg_pasilla, peso_alm_sana, factor, pr_alm_sana, pr_alm_defec, bonificacion, castigo, costos,"
                + " vr_kilo, vr_bruto, vr_inventario, aporte_asociado, descuento_cooperativo, retefuente,"
                + " descuento_financiero, otros_descuentos, neto_a_pagar, forma_pago, creado_por_usuario_id, creado_en,"
                + " numero_factura, anulacion_bloqueada)"
                + " SELECT current_date, ?, (SELECT min(id) FROM fondos), 'RN', (SELECT min(id) FROM productos), '1',"
                + " current_date, 0, '1', 'PRUEBA', 'ANULACION', 'C', 'VEREDA', '3000000000', 1, 100, 0, 100, 0, 0, 0,"
                + " 0, 0, 0, 0, 0, 0, 0, 0, 0, 1000, 0, 0, 0, 0, 0, 0, 1000, 'EFECTIVO', ?, now(), ?, ?",
                agencyId, userId, invoice, locked);
    }

    private AnnulmentCandidate find(int invoice) {
        return service.findByInvoice(agencyId, invoice).getFirst();
    }

    @Test
    void aPurchaseClosedInAccessCannotBeAnnulled() {
        AnnulmentCandidate locked = find(LOCKED);
        assertThat(locked.annulmentLocked()).isTrue();
        // D2 (V7): el prefijo sale del Registro de Control de la agencia, ya no de agencias.
        assertThat(locked.prefix()).isEqualTo("SDBU");
        assertThatThrownBy(() -> service.annul(agencyId, AnnulmentModule.DRY, locked.id()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage(PurchaseAnnulmentServiceImpl.ANNULMENT_LOCKED);
        assertThat(find(LOCKED).status()).isEqualTo("VALIDA");
    }

    @Test
    void aNewPurchaseIsAnnulledAndItsAmountsGoToZero() {
        AnnulmentCandidate open = find(OPEN);
        assertThat(open.annulmentLocked()).isFalse();
        AnnulmentCandidate annulled = service.annul(agencyId, AnnulmentModule.DRY, open.id());
        assertThat(annulled.status()).isEqualTo("ANULADA");
        assertThat(annulled.netToPay()).isZero();
        assertThat(annulled.annulledAt()).isNotNull();
    }
}
