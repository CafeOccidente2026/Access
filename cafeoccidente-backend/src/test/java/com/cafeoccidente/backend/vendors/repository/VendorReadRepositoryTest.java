package com.cafeoccidente.backend.vendors.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Deuda tecnica (S2077): la union de las 3 tablas de compra pasa a ser una constante fija. Debe ser
 * exactamente la que armaba antes purchase() (copiado aqui), asi Beneficiario y Cupos NESS no cambian;
 * HU-52 le agrega Cod_Prod al final (JOIN productos), que solo lee el informe Beneficiario.
 */
class VendorReadRepositoryTest {

    private static String oldPurchase(String table, String greenKg, String healthyPercentage, String freightDiscount) {
        return "SELECT p.agencia_id, a.nombre AS agency_name, p.fecha_compra, f.codigo AS fund_code, p.numero_factura,"
                + " p.cedula, " + greenKg + " AS kilos_verdes, p.kilos_netos, " + healthyPercentage + " AS factor,"
                + " p.vr_bruto, p.aporte_asociado, p.descuento_cooperativo, " + freightDiscount
                + " AS descuento_financiero, p.retefuente, p.otros_descuentos, p.neto_a_pagar, p.especial, pr.codigo AS cod_prod"
                + " FROM " + table + " p JOIN agencias a ON a.id = p.agencia_id JOIN fondos f ON f.id = p.fondo_id"
                + " JOIN productos pr ON pr.id = p.producto_id";
    }

    @Test
    void thePurchasesUnionIsTheSameSqlAsBefore() {
        assertThat(VendorReadRepository.PURCHASES).isEqualTo(String.join(" UNION ALL ",
                oldPurchase("compras_cafe_seco", "0", "p.factor", "p.descuento_financiero"),
                oldPurchase("compras_cafe_verde", "p.kilos_verdes", "0", "p.descuento_financiero"),
                oldPurchase("compras_pasilla", "0", "p.porc_almendra", "p.descuento_financiero")));
    }
}
