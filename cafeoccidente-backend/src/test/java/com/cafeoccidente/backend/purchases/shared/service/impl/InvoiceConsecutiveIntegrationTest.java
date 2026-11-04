package com.cafeoccidente.backend.purchases.shared.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.controlrecord.entity.ControlRecord;
import com.cafeoccidente.backend.controlrecord.service.ControlRecordService;
import com.cafeoccidente.backend.purchases.shared.service.InvoiceConsecutiveService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * HU-15 / Q7: consecutivo de factura por punto sobre el Registro de Control de la semilla, asi que
 * corre tambien en CI. Transaccional: no deja nada.
 */
@SpringBootTest
@Transactional
@SuppressWarnings("null")
class InvoiceConsecutiveIntegrationTest {

    @Autowired
    private InvoiceConsecutiveService service;
    @Autowired
    private ControlRecordService controlRecords;
    @Autowired
    private JdbcTemplate jdbc;

    private Long agencyId;
    private ControlRecord record;

    @BeforeEach
    void agencyWithControlRecord() {
        agencyId = jdbc.queryForObject("SELECT min(agencia_id) FROM registros_control WHERE activo", Long.class);
        record = controlRecords.getActive(agencyId);
        jdbc.update("DELETE FROM consecutivos_factura WHERE agencia_id = ?", agencyId);
    }

    private void lastNumber(int last) {
        jdbc.update("INSERT INTO consecutivos_factura (agencia_id, ultimo_numero) VALUES (?, ?)", agencyId, last);
    }

    private int stored() {
        return jdbc.queryForObject("SELECT ultimo_numero FROM consecutivos_factura WHERE agencia_id = ?", Integer.class, agencyId);
    }

    @Test
    void theFirstInvoiceOfAPointIsTheStartOfItsResolution() {
        assertThat(service.next(agencyId, record).invoiceNumber()).isEqualTo(record.getResolutionFrom());
        assertThat(service.take(agencyId, record)).isEqualTo(record.getResolutionFrom());
        assertThat(service.take(agencyId, record)).isEqualTo(record.getResolutionFrom() + 1);
    }

    @Test
    void showingTheNextNumberDoesNotConsumeIt() {
        lastNumber(record.getResolutionFrom() + 5);

        assertThat(service.next(agencyId, record).invoiceNumber()).isEqualTo(record.getResolutionFrom() + 6);
        assertThat(service.next(agencyId, record).invoiceNumber()).isEqualTo(record.getResolutionFrom() + 6);
        assertThat(service.next(agencyId, record).prefix()).isEqualTo(record.getPrefix());
        assertThat(stored()).isEqualTo(record.getResolutionFrom() + 5);
        assertThat(service.take(agencyId, record)).isEqualTo(record.getResolutionFrom() + 6);
    }

    @Test
    void aNewResolutionJumpsToTheStartOfItsRange() {
        // Como El Tambo: las facturas migradas son de la resolucion anterior, por debajo de "Desde".
        lastNumber(record.getResolutionFrom() - 100);

        assertThat(service.next(agencyId, record).invoiceNumber()).isEqualTo(record.getResolutionFrom());
        assertThat(service.take(agencyId, record)).isEqualTo(record.getResolutionFrom());
    }

    @Test
    void pastTheEndOfTheResolutionNoNumberIsGiven() {
        lastNumber(record.getResolutionTo());

        assertThatThrownBy(() -> service.next(agencyId, record))
                .isInstanceOf(BusinessRuleException.class).hasMessage(InvoiceConsecutiveServiceImpl.RANGE_EXHAUSTED);
        assertThatThrownBy(() -> service.take(agencyId, record))
                .isInstanceOf(BusinessRuleException.class).hasMessage(InvoiceConsecutiveServiceImpl.RANGE_EXHAUSTED);
    }

    @Test
    void theConsecutiveStartsAtTheHighestInvoiceAlreadyUsed() {
        // Semilla de la V12: ninguna agencia queda con un consecutivo por debajo de sus facturas.
        Long behind = jdbc.queryForObject("""
                SELECT count(*) FROM (
                    SELECT agencia_id, max(numero_factura) AS usado FROM (
                        SELECT agencia_id, numero_factura FROM compras_cafe_seco
                        UNION ALL SELECT agencia_id, numero_factura FROM compras_cafe_verde
                        UNION ALL SELECT agencia_id, numero_factura FROM compras_pasilla) c
                    GROUP BY agencia_id) u
                LEFT JOIN consecutivos_factura cf USING (agencia_id)
                WHERE cf.ultimo_numero IS NULL OR cf.ultimo_numero < u.usado""", Long.class);
        assertThat(behind).isZero();
    }

    @Test
    void anInvoiceNumberIsUniquePerPointNotAcrossPoints() {
        Long other = jdbc.queryForObject("SELECT min(id) FROM agencias WHERE id <> ?", Long.class, agencyId);
        purchase(agencyId, 990000001);
        purchase(other, 990000001);

        assertThatThrownBy(() -> purchase(agencyId, 990000001)).isInstanceOf(DuplicateKeyException.class);
    }

    private void purchase(Long agency, int invoice) {
        jdbc.update("INSERT INTO compras_cafe_seco (fecha_compra, agencia_id, fondo_id, especial, producto_id,"
                + " numero_anuncio, fecha_anuncio, pr_base_cps, cedula, nombres, apellidos, tipo_vendedor, direccion,"
                + " celular, sacos, kilos_brutos, destare, kilos_netos, peso_tot_alm, porc_merma, peso_tot_pasilla,"
                + " porc_kg_pasilla, peso_alm_sana, factor, pr_alm_sana, pr_alm_defec, bonificacion, castigo, costos,"
                + " vr_kilo, vr_bruto, vr_inventario, aporte_asociado, descuento_cooperativo, retefuente,"
                + " descuento_financiero, otros_descuentos, neto_a_pagar, forma_pago, creado_por_usuario_id, creado_en,"
                + " numero_factura)"
                + " SELECT current_date, ?, (SELECT min(id) FROM fondos), 'RN', (SELECT min(id) FROM productos), '1',"
                + " current_date, 0, '1', 'PRUEBA', 'CONSECUTIVO', 'C', 'VEREDA', '3000000000', 1, 100, 0, 100, 0, 0, 0,"
                + " 0, 0, 0, 0, 0, 0, 0, 0, 0, 1000, 0, 0, 0, 0, 0, 0, 1000, 'EFECTIVO', (SELECT min(id) FROM usuarios),"
                + " now(), ?", agency, invoice);
    }
}
