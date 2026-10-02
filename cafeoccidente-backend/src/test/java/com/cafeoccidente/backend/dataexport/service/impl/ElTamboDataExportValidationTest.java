package com.cafeoccidente.backend.dataexport.service.impl;

import static com.cafeoccidente.backend.dataexport.repository.DataExportRepository.ANNOUNCEMENT_COLUMNS;
import static com.cafeoccidente.backend.dataexport.repository.DataExportRepository.GROWER_COLUMNS;
import static com.cafeoccidente.backend.dataexport.repository.DataExportRepository.PURCHASE_COLUMNS;
import static org.assertj.core.api.Assertions.assertThat;

import com.cafeoccidente.backend.dataexport.repository.DataExportRepository;
import com.cafeoccidente.backend.dataexport.repository.ExportRow;
import com.cafeoccidente.backend.dataexport.service.DataExportService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
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
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Exportar Informacion" de El Tambo contra Access: cada columna de ComprasParaExportar,
 * SociosparaExportar y AnunciosParaExportar contra los CSV, ComprasTotal contra comprasTotal.XLS y
 * los encabezados/hojas de los 7 .xls contra los ejemplos reales. @Transactional: lo que la
 * exportacion marca se deshace al terminar. Se salta si faltan los CSV o la migracion.
 */
@SpringBootTest
@Transactional
@SuppressWarnings("null")
class ElTamboDataExportValidationTest {

    private static final Path DIR = Path.of("../docs/legacy-postgres-reference/eltambo");
    private static final Path EXAMPLES = Path.of("../docs/legacy-vba-export/eltambo/export-examples");

    /** Columnas de COMPRAS que el sistema nuevo no guarda: van vacias en el Excel. */
    private static final Set<String> EMPTY_PURCHASE_COLUMNS = Set.of("Vr_Kilo_Comp", "VrIncCalidad", "IncCalidad",
            "En_Inventario", "En_Caja", "En_Sum", "Plano", "Nuevo", "Remision", "EnProg", "EnCVRN", "CCosto", "SCCosto",
            "CodVend", "CodCiudad", "CodBodega", "TipoComp", "CodComp", "Espp");

    @Autowired
    private DataExportRepository repository;
    @Autowired
    private DataExportService service;
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
                String v = c[i];
                row.put(header.get(i), v.startsWith("\"") && v.endsWith("\"") && v.length() > 1
                        ? v.substring(1, v.length() - 1).replace("\"\"", "\"") : v);
            }
            rows.add(row);
        }
        return rows;
    }

    /** Valor exportado vs texto del CSV: numeros por valor, booleanos t/f, fechas por dia. */
    private static boolean same(Object exported, String access) {
        if (exported == null) {
            return access == null || access.isBlank();
        }
        if (exported instanceof Number n) {
            if (access == null || access.isBlank()) {
                return false;
            }
            // Compras guarda PorcAlmSana/PorcMerma con 2 decimales; Access el double completo
            // (92.10526315789474, 17.599999999999998): se compara a la escala guardada.
            BigDecimal value = new BigDecimal(n.toString());
            BigDecimal accessValue = new BigDecimal(access);
            if (value.scale() > 0 && accessValue.scale() > value.scale()) {
                accessValue = accessValue.setScale(value.scale(), java.math.RoundingMode.HALF_UP);
            }
            return value.compareTo(accessValue) == 0;
        }
        if (exported instanceof Boolean b) {
            return (b ? "t" : "f").equals(access);
        }
        if (exported instanceof LocalDate d) {
            return access != null && access.startsWith(d.toString());
        }
        return exported.toString().equals(access);
    }

    private static Map<String, Integer> mismatches(List<ExportRow> rows, List<String> columns,
            Map<String, Map<String, String>> accessByKey, Function<ExportRow, String> key, Function<String, String> csvColumn,
            Set<String> skip) {
        Map<String, Integer> diff = new TreeMap<>();
        for (ExportRow row : rows) {
            Map<String, String> access = accessByKey.get(key.apply(row));
            for (int c = 0; c < columns.size(); c++) {
                String column = columns.get(c);
                if (!skip.contains(column) && !same(row.values().get(c), access.get(csvColumn.apply(column)))) {
                    diff.merge(column, 1, Integer::sum);
                }
            }
        }
        return diff;
    }

    @Test
    void comprasParaExportarMatchesAccessColumnByColumn() throws IOException {
        // Anuladas incluidas: Forma_de_Pago = ANULADA y sus montos en 0, igual que en Access.
        Map<String, Map<String, String>> access = csv("compras_migrar.csv").stream()
                .collect(Collectors.toMap(r -> r.get("factura"), r -> r));
        List<ExportRow> rows = repository.purchases(agencyId, false).stream()
                .filter(r -> access.containsKey(String.valueOf(r.values().get(PURCHASE_COLUMNS.indexOf("Factura"))))).toList();
        assertThat(rows).hasSize(access.size());

        for (String column : EMPTY_PURCHASE_COLUMNS) {
            assertThat(rows).as(column).allMatch(r -> r.values().get(PURCHASE_COLUMNS.indexOf(column)) == null);
        }
        // NumCheque: Access guarda 0 cuando no hay cheque; el sistema nuevo no tiene numero -> vacio.
        int numCheque = PURCHASE_COLUMNS.indexOf("NumCheque");
        assertThat(rows).allMatch(r -> r.values().get(numCheque) != null
                || "0".equals(access.get(String.valueOf(r.values().get(PURCHASE_COLUMNS.indexOf("Factura")))).get("numcheque")));
        Set<String> skip = new java.util.HashSet<>(EMPTY_PURCHASE_COLUMNS);
        skip.add("NumCheque");
        Map<String, Integer> diff = mismatches(rows, PURCHASE_COLUMNS, access,
                r -> String.valueOf(r.values().get(PURCHASE_COLUMNS.indexOf("Factura"))), String::toLowerCase, skip);
        System.out.println("ComprasParaExportar - columnas distintas a Access: " + diff);
        assertThat(diff).isEmpty();
    }

    @Test
    void comprasTotalMatchesTheAccessExample() throws IOException {
        Set<String> invoices = csv("compras_migrar.csv").stream().map(r -> r.get("factura")).collect(Collectors.toSet());
        List<ExportRow> migrated = repository.purchases(agencyId, false).stream()
                .filter(r -> invoices.contains(String.valueOf(r.values().get(PURCHASE_COLUMNS.indexOf("Factura"))))).toList();
        List<List<Object>> totals = DataExportServiceImpl.purchaseTotals(migrated);
        try (InputStream in = Files.newInputStream(EXAMPLES.resolve("comprasTotal.XLS")); HSSFWorkbook book = new HSSFWorkbook(in)) {
            Row example = book.getSheetAt(0).getRow(1);
            assertThat(totals).hasSize(1);
            assertThat(totals.get(0).get(0)).isEqualTo(example.getCell(0).getStringCellValue());
            for (int c = 1; c < 10; c++) {
                assertThat(new BigDecimal(totals.get(0).get(c).toString()))
                        .as("comprasTotal columna " + c).isEqualByComparingTo(BigDecimal.valueOf(example.getCell(c).getNumericCellValue()));
            }
        }
    }

    /**
     * La migracion de Asociados recorto espacios/tabs iniciales, paso Tipo a mayuscula (vacio = "C") y
     * unifico la agencia escrita de varias formas ("El Tambo"/"EL TAMBO") en su valor de Access de la
     * tabla agency: Socios exporta lo guardado, asi que se compara contra el CSV con esa normalizacion.
     */
    @Test
    void sociosMatchesAccessColumnByColumn() throws IOException {
        Map<String, Map<String, String>> access = new HashMap<>();
        for (Map<String, String> r : csv("asociados_migrar.csv")) {
            Map<String, String> normalized = new HashMap<>();
            r.forEach((k, v) -> normalized.put(k, v.strip()));
            normalized.put("tipo", normalized.get("tipo").isEmpty() ? "C" : normalized.get("tipo").toUpperCase());
            access.putIfAbsent(r.get("idasociado"), normalized);
        }
        List<ExportRow> rows = repository.growers(null, false).stream()
                .filter(r -> access.containsKey((String) r.values().get(0))).toList();
        assertThat(rows).hasSize(access.size());
        Map<String, Integer> diff = mismatches(rows, GROWER_COLUMNS, access, r -> (String) r.values().get(0),
                String::toLowerCase, Set.of("Agencia"));
        System.out.println("SociosparaExportar - columnas distintas a Access: " + diff);
        assertThat(diff).isEmpty();
    }

    @Test
    void anunciosMatchesAccessColumnByColumn() throws IOException {
        Map<String, Map<String, String>> access = csv("anuncios_migrar.csv").stream()
                .collect(Collectors.toMap(r -> r.get("anuncio"), r -> r));
        List<ExportRow> rows = repository.announcements(agencyId, false).stream()
                .filter(r -> access.containsKey(String.valueOf(r.values().get(0)))).toList();
        assertThat(rows).hasSize(access.size());
        // Cupo/Entregados/Saldo: Access guarda 0 sin cupo asignado; aca no hay cupo -> vacio.
        Set<String> skip = Set.of("Cupo", "Entregados", "Saldo");
        assertThat(rows).allMatch(r -> r.values().get(ANNOUNCEMENT_COLUMNS.indexOf("Cupo")) == null);
        Map<String, Integer> diff = mismatches(rows, ANNOUNCEMENT_COLUMNS, access, r -> String.valueOf(r.values().get(0)),
                String::toLowerCase, skip);
        System.out.println("AnunciosParaExportar - columnas distintas a Access: " + diff);
        assertThat(diff).isEmpty();
    }

    /** AnuncioResumen: GROUP BY Anuncio, Agencia, Fondo, Especial, Exportado de las compras con Exportado = No. */
    @Test
    void anuncioResumenMatchesAccessPendingPurchases() throws IOException {
        List<Map<String, String>> pending = csv("compras_migrar.csv").stream()
                .filter(r -> "f".equals(r.get("exportado"))).toList();
        Map<String, BigDecimal[]> access = new TreeMap<>();
        for (Map<String, String> r : pending) {
            BigDecimal[] sums = access.computeIfAbsent(r.get("anuncio") + "|" + r.get("fondo") + "|" + r.get("especial"),
                    k -> new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            sums[0] = sums[0].add(new BigDecimal(r.get("kilos_netos")));
            sums[1] = sums[1].add(new BigDecimal(r.get("vr_inventario")));
            sums[2] = sums[2].add(new BigDecimal(r.get("neto_a_pagar")));
        }
        Set<String> invoices = pending.stream().map(r -> r.get("factura")).collect(Collectors.toSet());
        List<List<Object>> summary = DataExportServiceImpl.announcementSummary(repository.purchases(agencyId, true).stream()
                .filter(r -> invoices.contains(String.valueOf(r.values().get(PURCHASE_COLUMNS.indexOf("Factura"))))).toList());
        System.out.println("AnuncioResumen: Access " + access.size() + " grupos - sistema nuevo " + summary.size());
        assertThat(summary).hasSize(access.size());
        for (List<Object> row : summary) {
            BigDecimal[] expected = access.get(row.get(0) + "|" + row.get(2) + "|" + row.get(12));
            assertThat(expected).as(row.toString()).isNotNull();
            assertThat((BigDecimal) row.get(4)).isEqualByComparingTo(expected[0]);
            assertThat((BigDecimal) row.get(5)).isEqualByComparingTo(expected[1]);
            assertThat((BigDecimal) row.get(11)).isEqualByComparingTo(expected[2]);
            assertThat(row.get(13)).isEqualTo(false);
        }
    }

    /** Los 7 archivos con las hojas y encabezados de los .XLS reales; despues no queda nada pendiente. */
    @Test
    void exportProducesTheSevenAccessFilesAndMarksWhatItExported() throws IOException {
        Map<String, Sheet> generated = unzip(service.export(agencyId));
        Map<String, String> examples = Map.of("compras.xls", "compras.XLS", "anuncioresumen.xls", "AnuncioResumen.XLS",
                "comprastotal.xls", "comprasTotal.XLS", "Socios.xls", "Socios.XLS", "FutureBuys.xls", "FutureBuys.XLS",
                "Inventario.xls", "Inventario.XLS", "Anuncios.xls", "Anuncios.XLS");
        assertThat(generated.keySet()).containsExactlyInAnyOrderElementsOf(examples.keySet());
        for (Map.Entry<String, String> e : examples.entrySet()) {
            try (InputStream in = Files.newInputStream(EXAMPLES.resolve(e.getValue())); HSSFWorkbook book = new HSSFWorkbook(in)) {
                Sheet example = book.getSheetAt(0);
                Sheet sheet = generated.get(e.getKey());
                assertThat(sheet.getSheetName()).as(e.getKey()).isEqualTo(example.getSheetName());
                assertThat(header(sheet)).as(e.getKey()).isEqualTo(header(example));
            }
        }
        assertThat(repository.purchases(agencyId, true)).isEmpty();
        assertThat(repository.announcements(agencyId, true)).isEmpty();
        assertThat(repository.inventory(agencyId, true)).isEmpty();
        assertThat(repository.futurePurchases(agencyId, true)).isEmpty();
        assertThat(repository.growers(agencyId, true)).isEmpty();

        // Exportado Especial: desmarca el rango (ambos extremos) y lo vuelve a exportar.
        LocalDate day = LocalDate.of(2026, 5, 16);
        Map<String, Sheet> special = unzip(service.exportSpecial(agencyId, day, day));
        assertThat(special).containsKeys("AnuncioResumen.xls", "comprasTotal.xls");
        long sameDay = csv("compras_migrar.csv").stream()
                .filter(r -> r.get("fecha_compra").startsWith("2026-05-16")).count();
        assertThat(special.get("compras.xls").getLastRowNum()).isGreaterThanOrEqualTo((int) sameDay);
        assertThat(repository.purchases(agencyId, true)).isEmpty();
    }

    private static List<String> header(Sheet sheet) {
        List<String> names = new ArrayList<>();
        for (Cell cell : sheet.getRow(0)) {
            names.add(cell.getStringCellValue());
        }
        return names;
    }

    private static Map<String, Sheet> unzip(byte[] zip) throws IOException {
        Map<String, Sheet> sheets = new HashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                sheets.put(entry.getName(), new HSSFWorkbook(new ByteArrayInputStream(in.readAllBytes())).getSheetAt(0));
            }
        }
        return sheets;
    }
}
