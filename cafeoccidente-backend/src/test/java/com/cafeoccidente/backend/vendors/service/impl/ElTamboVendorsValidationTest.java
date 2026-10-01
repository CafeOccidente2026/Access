package com.cafeoccidente.backend.vendors.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.cafeoccidente.backend.vendors.dto.BeneficiaryRow;
import com.cafeoccidente.backend.vendors.dto.NessQuotaBalanceRow;
import com.cafeoccidente.backend.vendors.dto.NessQuotaPage;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Informes de Vendedores de El Tambo armados por el sistema nuevo contra los CSV de Access: consultas
 * Beneficiario / Beneficiario Resumen, CuposNess y NESSYRAINSALDO. Solo lee. Se salta si faltan los
 * CSV (docs/ esta en .gitignore) o si scripts/migrate_eltambo.py no corrio.
 */
@SpringBootTest
@Transactional
@SuppressWarnings("null")
class ElTamboVendorsValidationTest {

    private static final Path DIR = Path.of("../docs/legacy-postgres-reference/eltambo");

    @Autowired
    private VendorReportServiceImpl service;
    @Autowired
    private JdbcTemplate jdbc;

    private Long agencyId;

    @BeforeEach
    void requireMigratedData() {
        Assumptions.assumeTrue(Files.exists(DIR.resolve("compras_migrar.csv")), "Sin CSV de Access - se salta.");
        agencyId = jdbc.queryForObject("SELECT id FROM agency WHERE access_agency_value = 'EL TAMBO'", Long.class);
        Assumptions.assumeTrue(jdbc.queryForObject(
                "SELECT count(*) FROM dry_coffee_purchase WHERE agency_id = ?", Long.class, agencyId) > 0,
                "Compras sin migrar - corra scripts/migrate_eltambo.py primero.");
    }

    private static List<Map<String, String>> csv(String name) throws IOException {
        List<String> lines = Files.readAllLines(DIR.resolve(name), StandardCharsets.UTF_8);
        List<String> header = Arrays.asList(lines.get(0).split(","));
        List<Map<String, String>> rows = new ArrayList<>();
        for (String line : lines.subList(1, lines.size())) {
            String[] c = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
            Map<String, String> row = new HashMap<>();
            for (int i = 0; i < header.size(); i++) {
                row.put(header.get(i), c[i].replace("\"", ""));
            }
            rows.add(row);
        }
        return rows;
    }

    private static BigDecimal amount(String value) {
        return value == null || value.isBlank() ? BigDecimal.ZERO : new BigDecimal(value);
    }

    private static <T> BigDecimal sum(List<T> rows, Function<T, BigDecimal> value) {
        return rows.stream().map(value).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static String nessId(String cedula) {
        return cedula.endsWith(".0") ? cedula.substring(0, cedula.length() - 2) : cedula;
    }

    /** Access: COMPRAS INNER JOIN Asociados; las ANULADAS nunca se migraron (quedan fuera por construccion). */
    private List<Map<String, String>> accessBeneficiary(LocalDate from, LocalDate to) throws IOException {
        Set<String> associates = csv("asociados_migrar.csv").stream().map(r -> r.get("idasociado")).collect(Collectors.toSet());
        return csv("compras_migrar.csv").stream()
                .filter(r -> !"ANULADA".equals(r.get("forma_de_pago")))
                .filter(r -> associates.contains(r.get("cedula")))
                .filter(r -> from == null || !LocalDate.parse(r.get("fecha_compra").substring(0, 10)).isBefore(from)
                        && !LocalDate.parse(r.get("fecha_compra").substring(0, 10)).isAfter(to))
                .toList();
    }

    /** Solo las filas migradas: el sistema nuevo tiene ademas compras de prueba hechas en la web. */
    private static List<BeneficiaryRow> migrated(List<BeneficiaryRow> app, List<Map<String, String>> access) {
        Set<Integer> invoices = access.stream().map(r -> Integer.valueOf(r.get("factura"))).collect(Collectors.toSet());
        return app.stream().filter(r -> invoices.contains(r.invoiceNumber())).toList();
    }

    private static void compare(String label, List<Map<String, String>> access, List<BeneficiaryRow> app) {
        System.out.printf("%s: Access %d filas / KN %s / Neto %s - sistema nuevo %d filas / KN %s / Neto %s%n", label,
                access.size(), sum(access, r -> amount(r.get("kilos_netos"))), sum(access, r -> amount(r.get("neto_a_pagar"))),
                app.size(), sum(app, BeneficiaryRow::netKg), sum(app, BeneficiaryRow::netToPay));
        assertThat(app).as(label + " filas").hasSize(access.size());
        assertThat(sum(app, BeneficiaryRow::netKg)).isEqualByComparingTo(sum(access, r -> amount(r.get("kilos_netos"))));
        assertThat(sum(app, BeneficiaryRow::grossValue)).isEqualByComparingTo(sum(access, r -> amount(r.get("vr_bruto"))));
        assertThat(sum(app, BeneficiaryRow::associateContribution)).isEqualByComparingTo(sum(access, r -> amount(r.get("aporte_socio"))));
        assertThat(sum(app, BeneficiaryRow::cooperativeDiscount)).isEqualByComparingTo(sum(access, r -> amount(r.get("descuento_coop"))));
        assertThat(sum(app, BeneficiaryRow::freightDiscount)).isEqualByComparingTo(sum(access, r -> amount(r.get("descuento_fro"))));
        assertThat(sum(app, BeneficiaryRow::withholding)).isEqualByComparingTo(sum(access, r -> amount(r.get("retefuente"))));
        assertThat(sum(app, BeneficiaryRow::otherDiscounts)).isEqualByComparingTo(sum(access, r -> amount(r.get("otrosdescuentos"))));
        assertThat(sum(app, BeneficiaryRow::netToPay)).isEqualByComparingTo(sum(access, r -> amount(r.get("neto_a_pagar"))));
        assertThat(sum(app, BeneficiaryRow::greenKg)).isEqualByComparingTo(sum(access, r -> amount(r.get("kilos_verdes"))));
    }

    @Test
    void beneficiaryMatchesAccessForAllGrowers() throws IOException {
        List<Map<String, String>> access = accessBeneficiary(null, null);
        compare("Beneficiario (todas)", access, migrated(service.beneficiary(agencyId, "", null, null), access));
    }

    @Test
    void beneficiaryByPrefixAndInclusiveDates() throws IOException {
        LocalDate from = LocalDate.of(2026, 6, 1);
        LocalDate to = LocalDate.of(2026, 6, 30);
        List<Map<String, String>> access = accessBeneficiary(from, to).stream()
                .filter(r -> r.get("cedula").startsWith("59")).toList();
        List<BeneficiaryRow> app = migrated(service.beneficiary(agencyId, "59", from, to), access);
        compare("Beneficiario resumen (59*, junio)", access, app);
        assertThat(app).allMatch(r -> r.idNumber().startsWith("59"));
        assertThat(app).isSortedAccordingTo((a, b) -> a.idNumber().equals(b.idNumber())
                ? a.invoiceNumber().compareTo(b.invoiceNumber()) : a.idNumber().compareTo(b.idNumber()));
    }

    @Test
    void nessQuotasIsTheWholeNessTable() throws IOException {
        List<Map<String, String>> ness = csv("ness_migrar.csv");
        NessQuotaPage first = service.nessQuotas("", 0);
        assertThat(first.total()).isEqualTo(ness.size());
        assertThat(first.rows().get(0).idNumber()).isEqualTo(nessId(ness.get(0).get("cedula")));
        assertThat(first.rows().get(0).quota()).isEqualByComparingTo(ness.get(0).get("cupo"));
        long expected = ness.stream().filter(r -> nessId(r.get("cedula")).startsWith("5912")).count();
        assertThat(service.nessQuotas("5912", 0).total()).isEqualTo(expected);
    }

    /**
     * NESSYRAINSALDO sobre CUPOS (la tabla que dejo CalCuposSaldoNess en Access) y NESS. Las facturas
     * ANULADAS (0 kg) no se migraron: una cedula cuya unica compra del programa fue anulada sale en
     * Access con FACTURADOS 0 y en el sistema nuevo no sale (hoy 5248772 y 98215933).
     */
    @Test
    void quotaBalancesMatchNessYRainSaldo() throws IOException {
        Set<String> annulled = csv("compras_migrar.csv").stream()
                .filter(r -> "ANULADA".equals(r.get("forma_de_pago"))).map(r -> r.get("factura")).collect(Collectors.toSet());
        Map<String, BigDecimal> kilosByCedula = new HashMap<>();
        csv("cupos_migrar.csv").stream().filter(r -> !annulled.contains(r.get("factura")))
                .forEach(r -> kilosByCedula.merge(r.get("cedula"), amount(r.get("kilos")), BigDecimal::add));
        Map<String, BigDecimal> access = new TreeMap<>();
        for (Map<String, String> n : csv("ness_migrar.csv")) {
            String cedula = nessId(n.get("cedula"));
            if (kilosByCedula.containsKey(cedula)) {
                String key = cedula + "|" + n.get("programa") + "|" + amount(n.get("cupo")).stripTrailingZeros().toPlainString();
                access.merge(key, kilosByCedula.get(cedula), BigDecimal::add);
            }
        }
        List<NessQuotaBalanceRow> rows = service.nessQuotaBalances(agencyId);
        Map<String, BigDecimal> app = new TreeMap<>();
        rows.forEach(r -> app.put(r.idNumber() + "|" + r.program() + "|" + r.quota().stripTrailingZeros().toPlainString(),
                r.invoicedKg()));
        System.out.printf("NESSYRAINSALDO: Access %d filas - sistema nuevo %d filas%n", access.size(), app.size());
        assertThat(app.keySet()).isEqualTo(access.keySet());
        access.forEach((k, v) -> assertThat(app.get(k)).as(k).isEqualByComparingTo(v));
        assertThat(rows).allMatch(r -> r.balance().compareTo(r.quota().subtract(r.invoicedKg())) == 0);
    }
}
