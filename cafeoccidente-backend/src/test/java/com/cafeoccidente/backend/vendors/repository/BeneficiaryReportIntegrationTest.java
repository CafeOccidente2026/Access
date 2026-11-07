package com.cafeoccidente.backend.vendors.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.cafeoccidente.backend.vendors.dto.BeneficiaryRow;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** HU-52 / RF-28.2 contra Postgres: compras de pasilla insertadas a mano y revertidas al final. */
@SpringBootTest
@Transactional
class BeneficiaryReportIntegrationTest {

    private static final long AGENCY = 4L;
    private static final String SELLER = "77005201";
    private static final String OTHER = "77005202";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private VendorReadRepository vendors;

    private long otherAgency;
    private long user;

    @BeforeEach
    void setUp() {
        otherAgency = jdbc.queryForObject("SELECT min(id) FROM agencias WHERE id <> ?", Long.class, AGENCY);
        user = jdbc.queryForObject("SELECT min(id) FROM usuarios", Long.class);
        seller(SELLER, "AURA");
        seller(OTHER, "PEDRO");
        // Cod_Prod 0110002000002 (producto 1) y 0110002000010 (producto 4), facturas fuera del rango real.
        purchase(AGENCY, 990_001, SELLER, 1, "440");
        purchase(AGENCY, 990_002, SELLER, 4, "1000");
        purchase(AGENCY, 990_003, SELLER, 1, "250");
        purchase(otherAgency, 990_004, SELLER, 1, "300");
        purchase(AGENCY, 990_005, OTHER, 1, "80");
    }

    private void seller(String idNumber, String name) {
        jdbc.update("INSERT INTO terceros (identificacion, primer_nombre, primer_apellido, agencia_id, direccion,"
                + " telefono, tipo, activo, fallecido, retirado) VALUES (?, ?, 'PRUEBA52', ?, '', '', 'C', true, false, false)",
                idNumber, name, AGENCY);
    }

    private void purchase(long agency, int invoice, String idNumber, long product, String netKg) {
        jdbc.update("INSERT INTO compras_pasilla (fecha_compra, numero_factura, agencia_id, fondo_id, especial, producto_id,"
                + " numero_anuncio, fecha_anuncio, pr_base_cps, cedula, nombres, apellidos, tipo_vendedor, direccion, celular,"
                + " pr_punto, costos, peso_almendra, porc_almendra, sacos, kilos_brutos, destare, kilos_netos, vr_kilo, vr_bruto,"
                + " vr_inventario, aporte_asociado, descuento_cooperativo, retefuente, descuento_financiero, otros_descuentos,"
                + " neto_a_pagar, forma_pago, creado_por_usuario_id, creado_en)"
                + " SELECT ?, ?, ?, p.fondo_id, p.especial, p.id, '1', ?, 0, ?, 'N', 'A', 'C', '', '', 0, 0, 0, 0, 1, ?, 0, ?,"
                + " 1000, ? * 1000, ? * 1000, 0, 0, 0, 0, 0, ? * 1000, 'EFECTIVO', ?, now() FROM productos p WHERE p.id = ?",
                LocalDate.of(2026, 5, 9), invoice, agency, LocalDate.of(2026, 5, 9), idNumber,
                new BigDecimal(netKg), new BigDecimal(netKg), new BigDecimal(netKg), new BigDecimal(netKg),
                new BigDecimal(netKg), user, product);
    }

    private static BigDecimal netKg(List<BeneficiaryRow> rows) {
        return rows.stream().map(BeneficiaryRow::netKg).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    void hu52RowsComeByCedulaThenCodProdThenInvoiceAndAddUpTo1690() {
        List<BeneficiaryRow> rows = vendors.beneficiary(AGENCY, SELLER, null, null);

        assertThat(rows).extracting(BeneficiaryRow::invoiceNumber).containsExactly(990_001, 990_003, 990_002);
        assertThat(rows).extracting(BeneficiaryRow::productCode)
                .containsExactly("0110002000002", "0110002000002", "0110002000010");
        // RF-28.2: 440 + 1.000 + 250 = 1.690 KNetos.
        assertThat(netKg(rows)).isEqualByComparingTo("1690");
    }

    @Test
    void rn2823OnlyTheCurrentAgencyUnlessAllAgencies() {
        assertThat(vendors.beneficiary(AGENCY, SELLER, null, null)).extracting(BeneficiaryRow::invoiceNumber)
                .doesNotContain(990_004);
        assertThat(netKg(vendors.beneficiary(null, SELLER, null, null))).isEqualByComparingTo("1990");
    }

    @Test
    void rn2822AllBeneficiariesBringsEveryCedula() {
        List<BeneficiaryRow> rows = vendors.beneficiary(AGENCY, "770052", null, null);

        assertThat(rows).extracting(BeneficiaryRow::idNumber).containsExactly(SELLER, SELLER, SELLER, OTHER);
        assertThat(rows).extracting(BeneficiaryRow::firstName).containsExactly("AURA", "AURA", "AURA", "PEDRO");
    }
}
