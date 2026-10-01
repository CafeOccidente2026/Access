package com.cafeoccidente.backend.dataexport.service.impl;

import static com.cafeoccidente.backend.dataexport.repository.DataExportRepository.PURCHASE_COLUMNS;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.dataexport.repository.DataExportRepository;
import com.cafeoccidente.backend.dataexport.repository.ExportRow;
import com.cafeoccidente.backend.dataexport.service.DataExportService;
import com.cafeoccidente.backend.dataexport.service.XlsFiles;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DataExportServiceImpl implements DataExportService {

    static final List<String> SUMMARY_COLUMNS = List.of(
            "Anuncio", "Agencia", "Fondo", "SumaDeKilos_Verdes", "SumaDeKilos_Netos", "SumaDeVr_Inventario",
            "SumaDeAporte_Socio", "SumaDeDescuento_Coop", "SumaDeDescuento_Fro", "SumaDeRetefuente",
            "SumaDeOtrosDescuentos", "SumaDeNeto_a_Pagar", "Especial", "Exportado");

    static final List<String> TOTAL_COLUMNS = List.of(
            "Agencia", "SumaDeKilos_Verdes", "SumaDeKilos_Netos", "SumaDeVr_Bruto", "SumaDeAporte_Socio",
            "SumaDeDescuento_Coop", "SumaDeDescuento_Fro", "SumaDeRetefuente", "SumaDeOtrosDescuentos",
            "SumaDeNeto_a_Pagar");

    private static final List<String> SUMMARY_SUMS = List.of("Kilos_Verdes", "Kilos_Netos", "Vr_Inventario",
            "Aporte_Socio", "Descuento_Coop", "Descuento_Fro", "Retefuente", "OtrosDescuentos", "Neto_a_Pagar");

    private static final List<String> TOTAL_SUMS = List.of("Kilos_Verdes", "Kilos_Netos", "Vr_Bruto",
            "Aporte_Socio", "Descuento_Coop", "Descuento_Fro", "Retefuente", "OtrosDescuentos", "Neto_a_Pagar");

    private final DataExportRepository repository;

    public DataExportServiceImpl(DataExportRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public byte[] export(Long agencyId) {
        return run(agencyId, false);
    }

    @Override
    @Transactional
    public byte[] exportSpecial(Long agencyId, LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to)) {
            throw new BusinessRuleException("Indique Desde Fecha y Hasta Fecha (Desde no puede ser posterior a Hasta)");
        }
        repository.unmarkRange(agencyId, from, to);
        return run(agencyId, true);
    }

    /** Mismo orden que la macro: cada hoja se arma y despues se marcan sus filas de origen. */
    private byte[] run(Long agencyId, boolean special) {
        XlsFiles files = new XlsFiles();

        List<ExportRow> purchases = repository.purchases(agencyId, true);
        files.add("compras", "ComprasParaExportar", PURCHASE_COLUMNS, values(purchases));
        files.add(special ? "AnuncioResumen" : "anuncioresumen", "Anuncioresumen", SUMMARY_COLUMNS, announcementSummary(purchases));
        files.add(special ? "comprasTotal" : "comprastotal", "ComprasTotal", TOTAL_COLUMNS,
                purchaseTotals(repository.purchases(agencyId, false)));
        mark(purchases);

        List<ExportRow> growers = repository.growers(agencyId, true);
        files.add("Socios", "SociosparaExportar", DataExportRepository.GROWER_COLUMNS, values(growers));
        mark(growers);

        List<ExportRow> future = repository.futurePurchases(agencyId, true);
        files.add("FutureBuys", "FutureBuystoExport", DataExportRepository.FUTURE_COLUMNS, values(future));
        mark(future);

        List<ExportRow> inventory = repository.inventory(agencyId, true);
        files.add("Inventario", "SalidasparaExportar", DataExportRepository.INVENTORY_COLUMNS, values(inventory));
        mark(inventory);

        List<ExportRow> announcements = repository.announcements(agencyId, true);
        files.add("Anuncios", "AnunciosParaExportar", DataExportRepository.ANNOUNCEMENT_COLUMNS, values(announcements));
        mark(announcements);

        return files.zip();
    }

    private void mark(List<ExportRow> rows) {
        rows.stream().collect(Collectors.groupingBy(ExportRow::table, LinkedHashMap::new,
                        Collectors.mapping(ExportRow::id, Collectors.toCollection(java.util.LinkedHashSet::new))))
                .forEach(repository::markExported);
    }

    private static List<List<Object>> values(List<ExportRow> rows) {
        return rows.stream().map(ExportRow::values).toList();
    }

    private static Object column(ExportRow row, String name) {
        return row.values().get(PURCHASE_COLUMNS.indexOf(name));
    }

    /** "AnuncioResumen": GROUP BY Anuncio, Agencia, Fondo, Especial, Exportado de las compras pendientes. */
    static List<List<Object>> announcementSummary(List<ExportRow> purchases) {
        Map<List<Object>, List<ExportRow>> groups = new TreeMap<>(KEY_ORDER);
        purchases.forEach(p -> groups.computeIfAbsent(List.of(
                        text(column(p, "Anuncio")), text(column(p, "Agencia")), text(column(p, "Fondo")),
                        text(column(p, "Especial")), column(p, "Exportado")), k -> new ArrayList<>()).add(p));
        List<List<Object>> rows = new ArrayList<>();
        groups.forEach((key, group) -> {
            List<Object> row = new ArrayList<>(List.of(key.get(0), key.get(1), key.get(2)));
            SUMMARY_SUMS.forEach(c -> row.add(sum(group, c)));
            row.add(key.get(3));
            row.add(key.get(4));
            rows.add(row);
        });
        return rows;
    }

    /** "ComprasTotal": todas las compras (sin filtro de Exportado) sumadas por agencia. */
    static List<List<Object>> purchaseTotals(List<ExportRow> purchases) {
        Map<String, List<ExportRow>> groups = new TreeMap<>(purchases.stream()
                .collect(Collectors.groupingBy(p -> text(column(p, "Agencia")))));
        List<List<Object>> rows = new ArrayList<>();
        groups.forEach((agency, group) -> {
            List<Object> row = new ArrayList<>(List.of(agency));
            TOTAL_SUMS.forEach(c -> row.add(sum(group, c)));
            rows.add(row);
        });
        return rows;
    }

    /** Sum() de Access: ignora los Null; si todos son Null el resultado es Null. */
    private static BigDecimal sum(List<ExportRow> rows, String columnName) {
        return rows.stream().map(r -> (BigDecimal) toDecimal(column(r, columnName))).filter(Objects::nonNull)
                .reduce(BigDecimal::add).orElse(null);
    }

    private static Object toDecimal(Object value) {
        return value instanceof BigDecimal d ? d : value instanceof Number n ? new BigDecimal(n.toString()) : null;
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }

    private static final Comparator<List<Object>> KEY_ORDER = (a, b) -> {
        for (int i = 0; i < a.size(); i++) {
            int c = String.valueOf(a.get(i)).compareTo(String.valueOf(b.get(i)));
            if (c != 0) {
                return c;
            }
        }
        return 0;
    };
}
