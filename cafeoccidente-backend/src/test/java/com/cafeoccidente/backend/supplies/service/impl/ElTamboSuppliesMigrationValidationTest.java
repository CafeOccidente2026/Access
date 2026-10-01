package com.cafeoccidente.backend.supplies.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.cafeoccidente.backend.supplies.dto.SuppliesReportRow;
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
import java.util.function.Function;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Suministros (consultas SuministrosRP/LF), Caja Menor (MovCajaMenor) y Empaque (Mov Empaque) de El
 * Tambo, como los arma el sistema nuevo, contra los CSV de Access: filas y montos al peso. Solo lee.
 * Se salta si faltan los CSV (docs/ esta en .gitignore) o si scripts/migrate_eltambo.py no corrio.
 */
@SpringBootTest
@Transactional
// Advertencias de null de JDT sobre referencias a metodo y reduce, sin caso real.
@SuppressWarnings("null")
class ElTamboSuppliesMigrationValidationTest {

    private static final Path DIR = Path.of("../docs/legacy-postgres-reference/eltambo");
    private static final LocalDate TODAY = LocalDate.of(2026, 12, 31);

    @Autowired
    private SuppliesReportServiceImpl service;
    @Autowired
    private JdbcTemplate jdbc;

    private Long agencyId;

    @BeforeEach
    void requireMigratedData() {
        Assumptions.assumeTrue(Files.exists(DIR.resolve("suministros_migrar.csv")), "Sin CSV de Access - se salta.");
        agencyId = jdbc.queryForObject("SELECT id FROM agency WHERE access_agency_value = 'EL TAMBO'", Long.class);
        Assumptions.assumeTrue(jdbc.queryForObject(
                "SELECT count(*) FROM packaging_entry WHERE agency_id = ?", Long.class, agencyId) > 0,
                "Suministros/CajaMenor/Empaque sin migrar - corra scripts/migrate_eltambo.py primero.");
    }

    /** Filas del CSV como mapas columna -> valor (respeta comillas). */
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

    private static BigDecimal sum(List<Map<String, String>> rows, String column) {
        return rows.stream().map(r -> amount(r.get(column))).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal sum(List<SuppliesReportRow> rows, Function<SuppliesReportRow, BigDecimal> value) {
        return rows.stream().map(value).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static void compare(String label, List<Map<String, String>> access, String in, String out,
            List<SuppliesReportRow> app) {
        System.out.printf("%s: Access %d filas / %s / %s  -  sistema nuevo %d filas / %s / %s%n", label,
                access.size(), sum(access, in), sum(access, out), app.size(),
                sum(app, SuppliesReportRow::inflow), sum(app, SuppliesReportRow::outflow));
        assertThat(app).as(label + " filas").hasSize(access.size());
        assertThat(sum(app, SuppliesReportRow::inflow)).as(label + " entradas").isEqualByComparingTo(sum(access, in));
        assertThat(sum(app, SuppliesReportRow::outflow)).as(label + " salidas")
                .isEqualByComparingTo(sum(access, out));
    }

    @Test
    void suppliesMatchAccess() throws IOException {
        List<Map<String, String>> access = csv("suministros_migrar.csv");
        LocalDate cutoff = access.stream().map(r -> LocalDate.parse(r.get("fecha").substring(0, 10)))
                .max(LocalDate::compareTo).orElseThrow();
        // SUPUESTO de la carga de prueba (scripts/migrate_eltambo.py, SUPPLY_FUND_ASSUMPTIONS): la fila
        // "sumlf60-29-05" tenia Fondo "j" en Access y se migro como LF.
        Function<Map<String, String>, String> fund = r ->
                "sumlf60-29-05".equals(r.get("id_transaccion")) && "j".equals(r.get("fondo")) ? "LF" : r.get("fondo");
        for (String code : List.of("RP", "LF")) {
            compare("Suministros " + code, access.stream().filter(r -> code.equals(fund.apply(r))).toList(),
                    "valor_suministro", "vr_gastos_o_comp",
                    service.supplies(agencyId, code, TODAY).stream().filter(r -> !r.date().isAfter(cutoff)).toList());
        }
    }

    @Test
    void pettyCashMatchesAccess() throws IOException {
        // MovCajaMenor: WHERE Forma_de_Pago = "EFECTIVO"
        compare("Caja Menor", csv("cajamenor_migrar.csv").stream()
                        .filter(r -> "EFECTIVO".equals(r.get("forma_de_pago"))).toList(),
                "entradas", "salidas", service.pettyCashMovement(agencyId, TODAY));
    }

    @Test
    void packagingMatchesAccessByType() throws IOException {
        List<Map<String, String>> access = csv("empaque_migrar.csv");
        for (String type : List.of("NUEVO", "USADO")) {
            compare("Empaque " + type, access.stream().filter(r -> r.get("tipo").startsWith(type)).toList(),
                    "entradas", "salidas", service.packaging(agencyId, type, TODAY));
        }
    }
}
