package com.cafeoccidente.backend.supplies.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.cafeoccidente.backend.supplies.entity.CheckRelationMark;
import com.cafeoccidente.backend.supplies.repository.CheckRelationMarkRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * La Caja de El Tambo que arma el sistema nuevo al leer (compras + cash_entry + cheques girados de
 * supply_entry) tiene que dar, al peso, la misma Caja que tenia Access al corte (caja_migrar.csv), y
 * Relacion Cheques los mismos pendientes. Solo lee: no llama a checkRelation, que marca al imprimir.
 * Se salta si falta el CSV (docs/ esta en .gitignore) o si scripts/migrate_eltambo.py no corrio.
 */
@SpringBootTest
@Transactional
class ElTamboCashBookMigrationValidationTest {

    private static final Path CSV_PATH = Path.of("../docs/legacy-postgres-reference/eltambo/caja_migrar.csv");

    @Autowired
    private SuppliesReportServiceImpl service;
    @Autowired
    private CheckRelationMarkRepository marks;
    @Autowired
    private JdbcTemplate jdbc;

    // Advertencias de null de JDT sobre referencias a metodo del record, sin caso real.
    @SuppressWarnings("null")
    @Test
    void cashBookMatchesAccessAtCutoff() throws IOException {
        Assumptions.assumeTrue(Files.exists(CSV_PATH), "No esta " + CSV_PATH + " - se salta.");
        Long agencyId = jdbc.queryForObject("SELECT id FROM agency WHERE access_agency_value = 'EL TAMBO'", Long.class);
        Assumptions.assumeTrue(jdbc.queryForObject(
                "SELECT count(*) FROM cash_entry WHERE agency_id = ?", Long.class, agencyId) > 0,
                "Caja de El Tambo sin migrar - corra scripts/migrate_eltambo.py primero.");

        List<String> lines = Files.readAllLines(CSV_PATH, StandardCharsets.UTF_8);
        List<String> header = Arrays.asList(lines.get(0).split(","));
        BigDecimal accessInflow = BigDecimal.ZERO;
        BigDecimal accessOutflow = BigDecimal.ZERO;
        LocalDate cutoff = LocalDate.MIN;
        int accessPending = 0;
        // Forma de pago -> [filas, entradas, salidas] de la consulta FormaPago de Access.
        Map<String, BigDecimal[]> accessByMethod = new TreeMap<>();
        for (String line : lines.subList(1, lines.size())) {
            String[] c = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
            accessInflow = accessInflow.add(new BigDecimal(c[header.indexOf("entradas")]));
            accessOutflow = accessOutflow.add(new BigDecimal(c[header.indexOf("salidas")]));
            add(accessByMethod, c[header.indexOf("forma_de_pago")], new BigDecimal(c[header.indexOf("entradas")]),
                    new BigDecimal(c[header.indexOf("salidas")]));
            LocalDate date = LocalDate.parse(c[header.indexOf("fecha")].substring(0, 10));
            cutoff = date.isAfter(cutoff) ? date : cutoff;
            if (Double.parseDouble(c[header.indexOf("cheque")]) > 0 && "f".equals(c[header.indexOf("rel_cheques")])) {
                accessPending++;
            }
        }

        // Hasta el ultimo dia de Access: despues del corte solo hay movimientos del sistema nuevo.
        List<SuppliesReportServiceImpl.Line> book = service.cashBook(agencyId, LocalDate.of(cutoff.getYear(), 1, 1), cutoff);
        BigDecimal inflow = book.stream().map(SuppliesReportServiceImpl.Line::inflow).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal outflow = book.stream().map(SuppliesReportServiceImpl.Line::outflow).reduce(BigDecimal.ZERO, BigDecimal::add);

        List<SuppliesReportServiceImpl.Line> checks = book.stream()
                .filter(l -> l.checkNumber() != null && l.checkNumber() > 0)
                .toList();
        Set<CheckRelationMark.Key> marked = new HashSet<>();
        marks.findAllById(checks.stream().map(SuppliesReportServiceImpl.Line::key).toList())
                .forEach(m -> marked.add(new CheckRelationMark.Key(m.getSource(), m.getSourceId())));
        List<String> pending = checks.stream().filter(l -> !marked.contains(l.key()))
                .map(SuppliesReportServiceImpl.Line::transactionId).toList();

        System.out.println("=== Caja El Tambo al " + cutoff + " (Access vs sistema nuevo) ===");
        System.out.println("Entradas: " + accessInflow + " vs " + inflow);
        System.out.println("Salidas:  " + accessOutflow + " vs " + outflow);
        System.out.println("Saldo:    " + accessInflow.subtract(accessOutflow) + " vs " + inflow.subtract(outflow));
        System.out.println("Relacion Cheques pendientes: " + accessPending + " vs " + pending.size());

        // Rel Formas de Pago (FormaPago: Fecha Between Desde And Hasta+1) hasta el corte, por forma de pago.
        Map<String, BigDecimal[]> appByMethod = new TreeMap<>();
        service.paymentMethods(agencyId, LocalDate.of(cutoff.getYear(), 1, 1), cutoff.minusDays(1), "*")
                .forEach(r -> add(appByMethod, r.paymentMethod(), r.inflow(), r.outflow()));
        appByMethod.forEach((method, v) -> System.out.println("Formas de Pago " + method + ": "
                + Arrays.toString(accessByMethod.get(method)) + " vs " + Arrays.toString(v)));

        // Access nunca copiaba compras en $0 a Caja (ActualizaCaja WHERE FPef > 0).
        assertThat(book).noneMatch(l -> l.inflow().signum() == 0 && l.outflow().signum() == 0);
        System.out.println("Filas de Caja: " + (lines.size() - 1) + " vs " + book.size());
        // Pagos mixtos (purchase_payment_split): una fila por forma, como ActualizaCaja/ActualizaCh.
        assertThat(book).hasSize(lines.size() - 1);
        assertThat(inflow).isEqualByComparingTo(accessInflow);
        assertThat(outflow).isEqualByComparingTo(accessOutflow);
        assertThat(pending).hasSize(accessPending).contains("che5480", "che5481");
        assertThat(appByMethod.keySet()).isEqualTo(accessByMethod.keySet());
        accessByMethod.forEach((method, expected) -> {
            BigDecimal[] actual = appByMethod.get(method);
            for (int i = 0; i < expected.length; i++) {
                assertThat(actual[i]).as("Formas de Pago " + method + " [filas, entradas, salidas]")
                        .isEqualByComparingTo(expected[i]);
            }
        });
    }

    private static void add(Map<String, BigDecimal[]> totals, String method, BigDecimal inflow, BigDecimal outflow) {
        BigDecimal[] t = totals.computeIfAbsent(method,
                k -> new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
        t[0] = t[0].add(BigDecimal.ONE);
        t[1] = t[1].add(inflow);
        t[2] = t[2].add(outflow);
    }
}
