package com.cafeoccidente.backend.purchases.drycoffee.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.cafeoccidente.backend.controlrecord.entity.ControlRecord;
import com.cafeoccidente.backend.controlrecord.service.ControlRecordService;
import com.cafeoccidente.backend.purchases.drycoffee.dto.DryCoffeePurchaseRequest;
import com.cafeoccidente.backend.purchases.drycoffee.entity.DryCoffeePurchase;
import com.cafeoccidente.backend.purchases.drycoffee.repository.DryCoffeePurchaseRepository;
import com.cafeoccidente.backend.purchases.future.entity.Announcement;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Regresion historica: recalcula TODAS las compras reales de Cafe Seco migradas de El Tambo
 * (compras_migrar.csv, 904 filas) con el {@link DryCoffeePurchaseCalculator} ACTUAL y compara,
 * fila por fila, TODOS los campos a la vez contra lo que realmente salio en Access - no un campo
 * aislado (criterio establecido en docs/informe-auditoria-completa-2026-09-22.md seccion 3).
 *
 * <p>Reemplaza al script que produjo el 42.72%/35.20% citado en el commit {@code 05d2b1b}: ese
 * script nunca se comiteo y no era reproducible. Este test SI queda versionado - correlo con
 * {@code mvn test -Dtest=DryCoffeePurchaseCalculatorHistoricalValidationTest}.
 *
 * <p><b>Requisitos para que corra (si faltan, se salta con {@link Assumptions}, nunca falla el
 * build por esto):</b>
 * <ol>
 *   <li>{@code docs/legacy-postgres-reference/eltambo/compras_migrar.csv} presente en disco
 *       (la carpeta {@code docs/} esta en {@code .gitignore}, cada quien la tiene solo local).</li>
 *   <li>Los datos de El Tambo ya cargados en la base contra la que corren los tests, con
 *       {@code python scripts/migrate_eltambo.py} (agency, control_record, grower, announcement,
 *       dry_coffee_purchase).</li>
 * </ol>
 *
 * <p><b>Metodologia y limitaciones conocidas (leer antes de confiar en el numero que imprime):</b>
 * <ul>
 *   <li>El {@link ControlRecord} usado es el UNICO snapshot vigente hoy en la base (no hay
 *       historial de RegControl por fecha) - si algun parametro cambio entre la fecha de la compra
 *       mas vieja y hoy, esas filas se recalculan con parametros que pueden no ser los que
 *       realmente regian entonces.</li>
 *   <li>{@code growerType} y {@code withholdingExempt} no vienen de una columna directa en
 *       compras_migrar.csv: los resolvio {@code scripts/migrate_eltambo.py} (join contra "tipo" de
 *       asociados_migrar.csv, y withholding_exempt = retefuente historico == 0) al insertar en
 *       dry_coffee_purchase - este test lee esos campos YA resueltos de la fila persistida, no los
 *       vuelve a inferir.</li>
 *   <li>El acumulado mensual (Texto105/Texto107) se reconstruye ordenando las compras de cada
 *       cedula por fecha+factura y sumando el Vr_Bruto/Retefuente HISTORICO (no el recalculado) de
 *       las compras anteriores del mismo mes calendario - asi es como el sistema real lo habria
 *       visto en su momento. La formula de origen de ese acumulado sigue sin confirmar contra el
 *       VBA (ver informe de formulas); este test no resuelve esa limitacion, solo la reproduce.</li>
 *   <li>Filas con {@code forma_de_pago = ANULADA} ya fueron excluidas por el script de migracion,
 *       no llegan a estar en {@code dry_coffee_purchase}.</li>
 * </ul>
 */
@SpringBootTest
class DryCoffeePurchaseCalculatorHistoricalValidationTest {

    private static final Path CSV_PATH =
            Path.of("../docs/legacy-postgres-reference/eltambo/compras_migrar.csv");
    private static final String EL_TAMBO = "EL TAMBO";
    private static final String ANULADA = "ANULADA";

    /** Piso de regresion (NO una meta): protege contra que un cambio futuro empeore la precision
     *  en silencio. Medido la primera vez que corrio este test contra el checkout actual - ver
     *  docs/informe-auditoria-completa-2026-09-22.md seccion 3 para el numero de referencia previo
     *  (42.72%/35.20%, de un script no reproducible). Subir este piso si una mejora real lo supera
     *  con margen, nunca bajarlo para que un cambio que empeora las cosas pase el test. */
    private static final double MINIMUM_ALL_FIELDS_MATCH_PERCENTAGE = 35.0;

    @Autowired
    private DryCoffeePurchaseCalculator calculator;
    @Autowired
    private DryCoffeePurchaseRepository purchaseRepository;
    @Autowired
    private ControlRecordService controlRecordService;
    @PersistenceContext
    private EntityManager entityManager;

    // El analisis de null de JDT no logra propagar el filtrado previo (o que estos getters de JPA
    // siempre vienen poblados aca) al tipo de las referencias a metodo usadas como Comparator/
    // BiFunction mas abajo - advertencias sin caso real, no un bug.
    @SuppressWarnings("null")
    @Test
    void recalculatesEveryHistoricalElTamboPurchaseAndReportsExactMatchRate() throws IOException {
        Assumptions.assumeTrue(Files.exists(CSV_PATH),
                "No esta " + CSV_PATH + " en este checkout (docs/ esta en .gitignore) - "
                        + "se salta la validacion historica.");

        Set<Integer> historicalInvoiceNumbers = validInvoiceNumbersFromCsv();

        List<DryCoffeePurchase> historicalPurchases = purchaseRepository.findAll().stream()
                .filter(p -> EL_TAMBO.equalsIgnoreCase(p.getAgency().getAccessAgencyValue()))
                .filter(p -> historicalInvoiceNumbers.contains(p.getInvoiceNumber()))
                .sorted(Comparator.comparing(DryCoffeePurchase::getPurchaseDate)
                        .thenComparing(DryCoffeePurchase::getInvoiceNumber))
                .toList();

        Assumptions.assumeTrue(!historicalPurchases.isEmpty(),
                "El CSV esta pero dry_coffee_purchase no tiene esas facturas todavia - corra "
                        + "'python scripts/migrate_eltambo.py' contra la base de este entorno primero.");

        Map<Long, ControlRecord> controlRecordByAgencyId = new HashMap<>();
        Map<String, List<MonthlyEntry>> historyByIdNumber = new HashMap<>();

        int rowsCompared = 0;
        int allFieldsMatch = 0;
        int netToPayMatch = 0;
        int calculationErrors = 0;
        Map<String, Integer> mismatchesByField = new HashMap<>();

        for (DryCoffeePurchase historical : historicalPurchases) {
            ControlRecord controlRecord = controlRecordByAgencyId.computeIfAbsent(
                    historical.getAgency().getId(), controlRecordService::getActive);

            Announcement announcement = findHistoricalAnnouncement(historical);
            if (announcement == null) {
                calculationErrors++;
                continue;
            }

            YearMonth yearMonth = YearMonth.from(historical.getPurchaseDate());
            BigDecimal[] accumulated = priorMonthlyTotals(historyByIdNumber, historical.getIdNumber(), yearMonth);

            DryCoffeePurchaseCalculation recalculated;
            try {
                recalculated = calculator.calculate(
                        toRequest(historical), controlRecord, announcement.getBasePriceLoad(),
                        announcement.getDefectiveUnitPrice(), accumulated[0], accumulated[1]);
            } catch (RuntimeException ex) {
                calculationErrors++;
                continue;
            } finally {
                historyByIdNumber.computeIfAbsent(historical.getIdNumber(), id -> new ArrayList<>())
                        .add(new MonthlyEntry(yearMonth, historical.getGrossValue(), historical.getWithholding()));
            }

            rowsCompared++;
            List<String> mismatches = fieldMismatches(historical, recalculated);
            if (mismatches.isEmpty()) {
                allFieldsMatch++;
            } else {
                mismatches.forEach(field -> mismatchesByField.merge(field, 1, Integer::sum));
            }
            if (historical.getNetToPay().compareTo(recalculated.netToPay()) == 0) {
                netToPayMatch++;
            }
        }

        double allFieldsPct = rowsCompared == 0 ? 0 : 100.0 * allFieldsMatch / rowsCompared;
        double netToPayPct = rowsCompared == 0 ? 0 : 100.0 * netToPayMatch / rowsCompared;

        System.out.println("=== Validacion historica Cafe Seco (El Tambo, compras_migrar.csv) ===");
        System.out.printf("Filas historicas encontradas en la base: %d%n", historicalPurchases.size());
        System.out.printf("Filas efectivamente comparadas: %d (errores de calculo/sin anuncio: %d)%n",
                rowsCompared, calculationErrors);
        System.out.printf("Coincidencia exacta TODOS los campos a la vez: %.2f%% (%d/%d)%n",
                allFieldsPct, allFieldsMatch, rowsCompared);
        System.out.printf("Coincidencia exacta solo Neto a Pagar: %.2f%% (%d/%d)%n",
                netToPayPct, netToPayMatch, rowsCompared);
        System.out.println("Filas que fallaron cada campo (una fila puede fallar mas de uno):");
        mismatchesByField.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(e -> System.out.printf("  %-24s %d%n", e.getKey(), e.getValue()));

        assertThat(allFieldsPct)
                .as("Piso de regresion historica (ver Javadoc de esta clase) - si esto baja, algo "
                        + "empeoro la precision de la cascada de Cafe Seco frente a Access")
                .isGreaterThanOrEqualTo(MINIMUM_ALL_FIELDS_MATCH_PERCENTAGE);
    }

    private Announcement findHistoricalAnnouncement(DryCoffeePurchase historical) {
        List<Announcement> matches = entityManager.createQuery(
                        "SELECT a FROM Announcement a WHERE a.announcementNumber = :number "
                                + "AND a.agency.id = :agencyId", Announcement.class)
                .setParameter("number", historical.getAnnouncementNumber())
                .setParameter("agencyId", historical.getAgency().getId())
                .getResultList();
        return matches.isEmpty() ? null : matches.get(0);
    }

    private static DryCoffeePurchaseRequest toRequest(DryCoffeePurchase p) {
        return new DryCoffeePurchaseRequest(
                p.getAgency().getId(), p.getFund().getId(), p.getInvoiceNumber(), p.getSpecialType(),
                p.getIdNumber(), p.getFirstName(), p.getLastName(), p.getGrowerType(), p.getAddress(),
                p.getCellphone(), p.getBagsCount(), p.getGrossKg(), p.getTareKg(),
                p.getTotalStoredWeight(), p.getDefectiveStoredWeight(), p.getHealthyStoredWeight(),
                p.getHealthyUnitPrice(), p.getBonus(), p.getPenalty(), p.getCosts(),
                p.isWithholdingExempt(), p.getFreightDiscount(), p.getOtherDiscounts(),
                p.getPaymentMethod(), p.getCheckNumber());
    }

    /** Suma el Vr_Bruto/Retefuente HISTORICO de las compras ya registradas para esta cedula en el
     *  mismo mes calendario, en el orden en que realmente ocurrieron - ver limitaciones en el
     *  Javadoc de la clase. */
    private static BigDecimal[] priorMonthlyTotals(
            Map<String, List<MonthlyEntry>> historyByIdNumber, String idNumber, YearMonth yearMonth) {
        BigDecimal grossValue = BigDecimal.ZERO;
        BigDecimal withholding = BigDecimal.ZERO;
        for (MonthlyEntry entry : historyByIdNumber.getOrDefault(idNumber, List.of())) {
            if (entry.yearMonth().equals(yearMonth)) {
                grossValue = grossValue.add(entry.grossValue());
                withholding = withholding.add(entry.withholding());
            }
        }
        return new BigDecimal[] {grossValue, withholding};
    }

    private record MonthlyEntry(YearMonth yearMonth, BigDecimal grossValue, BigDecimal withholding) {
    }

    private static List<String> fieldMismatches(DryCoffeePurchase historical, DryCoffeePurchaseCalculation r) {
        List<String> mismatches = new ArrayList<>();
        checkField(mismatches, "basePriceLoad", historical.getBasePriceLoad(), r.basePriceLoad());
        checkField(mismatches, "netKg", historical.getNetKg(), r.netKg());
        checkField(mismatches, "wastePercentage", historical.getWastePercentage(), r.wastePercentage());
        checkField(mismatches, "defectivePercentage", historical.getDefectivePercentage(), r.defectivePercentage());
        checkField(mismatches, "healthyPercentage", historical.getHealthyPercentage(), r.healthyPercentage());
        checkField(mismatches, "unitPrice", historical.getUnitPrice(), r.unitPrice());
        checkField(mismatches, "grossValue", historical.getGrossValue(), r.grossValue());
        checkField(mismatches, "associateContribution", historical.getAssociateContribution(), r.associateContribution());
        checkField(mismatches, "cooperativeDiscount", historical.getCooperativeDiscount(), r.cooperativeDiscount());
        checkField(mismatches, "withholding", historical.getWithholding(), r.withholding());
        checkField(mismatches, "netToPay", historical.getNetToPay(), r.netToPay());
        return mismatches;
    }

    private static void checkField(List<String> mismatches, String name, BigDecimal expected, BigDecimal actual) {
        if (expected.compareTo(actual) != 0) {
            mismatches.add(name);
        }
    }

    /** Factura (invoice_number) de las filas de compras_migrar.csv que son de El Tambo y no fueron
     *  anuladas - el mismo filtro que aplica scripts/migrate_eltambo.py al insertar. */
    private static Set<Integer> validInvoiceNumbersFromCsv() throws IOException {
        List<String> lines = Files.readAllLines(CSV_PATH);
        String[] header = lines.get(0).split(",", -1);
        int facturaIdx = indexOf(header, "factura");
        int agenciaIdx = indexOf(header, "agencia");
        int formaPagoIdx = indexOf(header, "forma_de_pago");

        Set<Integer> invoiceNumbers = new HashSet<>();
        for (String line : lines.subList(1, lines.size())) {
            String[] columns = line.split(",", -1);
            if (!EL_TAMBO.equalsIgnoreCase(columns[agenciaIdx])
                    || ANULADA.equalsIgnoreCase(columns[formaPagoIdx])) {
                continue;
            }
            invoiceNumbers.add((int) Double.parseDouble(columns[facturaIdx]));
        }
        return invoiceNumbers;
    }

    private static int indexOf(String[] header, String column) {
        for (int i = 0; i < header.length; i++) {
            if (header[i].equalsIgnoreCase(column)) {
                return i;
            }
        }
        throw new IllegalStateException("Columna '" + column + "' no encontrada en compras_migrar.csv");
    }
}
