package com.cafeoccidente.backend.inventory.service;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.inventory.dto.InventoryReportRow;
import com.cafeoccidente.backend.inventory.dto.ProductCodeOption;
import com.cafeoccidente.backend.inventory.entity.InventoryMovement;
import com.cafeoccidente.backend.inventory.entity.Remission;
import com.cafeoccidente.backend.inventory.entity.RemissionLine;
import com.cafeoccidente.backend.inventory.repository.InventoryMovementRepository;
import com.cafeoccidente.backend.inventory.repository.RemissionLineRepository;
import com.cafeoccidente.backend.purchases.shared.repository.ProductCodeRepository;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import org.springframework.stereotype.Service;

/**
 * "Reporte Inventario por Cod." (INVXCODPROD) y "por Esp." (INVENTARIO, consulta INV). Mismas filas y
 * formulas; cambia el filtro (Cod_Prod o Especial) y el orden del reporte.
 */
@Service
public class InventoryReportService {

    private final InventoryMovementRepository inventoryMovementRepository;
    private final RemissionLineRepository remissionLineRepository;
    private final ProductCodeRepository productCodeRepository;

    public InventoryReportService(
            InventoryMovementRepository inventoryMovementRepository,
            RemissionLineRepository remissionLineRepository,
            ProductCodeRepository productCodeRepository) {
        this.inventoryMovementRepository = inventoryMovementRepository;
        this.remissionLineRepository = remissionLineRepository;
        this.productCodeRepository = productCodeRepository;
    }

    public List<ProductCodeOption> productCodes() {
        return productCodeRepository.findAllByOrderByCodeAsc().stream()
                .map(p -> new ProductCodeOption(p.getCode(), p.getName()))
                .toList();
    }

    /** Exactamente uno de productCode / specialType. */
    public List<InventoryReportRow> report(Long agencyId, String productCode, String specialType, LocalDate today) {
        String code = productCode != null && !productCode.isBlank() ? productCode.trim() : null;
        String special = specialType != null && !specialType.isBlank() ? specialType.trim() : null;
        boolean byCode = code != null;
        if (byCode == (special != null)) {
            throw new BusinessRuleException("Indique el código de producto o la especialidad");
        }
        Predicate<InventoryMovement> matches = byCode
                ? m -> m.getProductCode().getCode().equals(code)
                : m -> m.getSpecialType().equals(special);

        // Cierre de año, fase 1: Access borraba el año anterior ("eliminainventarioañoanterior"); aca nada se borra.
        // El reporte sigue mostrando el año en curso, pero lo anterior llega como una fila de saldo inicial.
        LocalDate from = today.withDayOfYear(1);
        LocalDate to = today.withDayOfYear(today.lengthOfYear());

        List<Sortable> rows = new ArrayList<>();
        Opening opening = new Opening();
        inventoryMovementRepository.findByAgencyIdAndPurchaseDateLessThanEqual(agencyId, to).stream()
                .filter(matches)
                .forEach(m -> {
                    if (m.getPurchaseDate().isBefore(from)) {
                        opening.add(m.getSpecialType(), m.getNetKg(), m.getInventoryValue(), m.getHealthyPercentage());
                    } else {
                        rows.add(new Sortable(m.getCreatedAt(), 0, m.getId(), new InventoryReportRow(
                                m.getPurchaseDate(), m.getInvoiceNumber(), null, m.getProductCode().getCode(),
                                m.getSpecialType(), m.getNetKg(), m.getHealthyPercentage(), m.getInventoryValue(),
                                null, null, null, null)));
                    }
                });
        remissionLineRepository.findByRemissionAgencyIdAndRemissionRemissionDateLessThanEqual(agencyId, to).stream()
                .filter(line -> Remission.VIGENTE.equals(line.getRemission().getStatus()))
                .filter(line -> matches.test(line.getInventoryMovement()))
                .forEach(line -> {
                    InventoryReportRow exit = exitRow(line).row();
                    if (exit.date().isBefore(from)) {
                        opening.add(exit.specialType(), exit.quantity().negate(), exit.outputValue().negate(),
                                exit.exitPercentage());
                    } else {
                        rows.add(exitRow(line));
                    }
                });

        Comparator<Sortable> order = byCode
                // INVXCODPROD: BreakLevel Id = orden de ingreso a la tabla INVENTARIO.
                ? Comparator.comparing((Sortable s) -> s.createdAt()).thenComparing(s -> s.kind()).thenComparing(s -> s.id())
                // INVENTARIO: BreakLevel Fecha, Factura, Remision (ascendente, Null primero como Access).
                : Comparator.comparing((Sortable s) -> s.row().date())
                        .thenComparing(s -> s.row().invoiceNumber(), Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(s -> s.row().remissionNumber(), Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(s -> s.id());
        List<InventoryReportRow> sorted = new ArrayList<>(rows.stream().sorted(order).map(s -> s.row()).toList());
        if (opening.any) {
            sorted.addFirst(opening.row(from, byCode ? code : null));
        }
        return sorted;
    }

    /**
     * «Saldo inicial — viene de <año>»: lo que dejaron los años anteriores, calculado al leer (no se copia nada). Va
     * como una entrada para que el reporte siga su suma corrida: kilos, valor y el % ponderado que quedaba
     * (Acum / Saldo_Cantidad).
     */
    private static final class Opening {
        private boolean any;
        private String specialType;
        private BigDecimal kg = BigDecimal.ZERO;
        private BigDecimal value = BigDecimal.ZERO;
        private BigDecimal acum = BigDecimal.ZERO;

        private void add(String special, BigDecimal kilos, BigDecimal amount, BigDecimal percentage) {
            any = true;
            specialType = special;
            BigDecimal k = kilos == null ? BigDecimal.ZERO : kilos;
            kg = kg.add(k);
            value = value.add(amount == null ? BigDecimal.ZERO : amount);
            acum = acum.add(k.multiply(percentage == null ? BigDecimal.ZERO : percentage));
        }

        private InventoryReportRow row(LocalDate from, String code) {
            BigDecimal percentage = kg.signum() == 0 ? BigDecimal.ZERO : acum.divide(kg, MathContext.DECIMAL64);
            return new InventoryReportRow(from.minusDays(1), null, null, code, specialType, kg, percentage, value,
                    null, null, null, "Saldo inicial — viene de " + (from.getYear() - 1));
        }
    }

    private static Sortable exitRow(RemissionLine line) {
        InventoryMovement origin = line.getInventoryMovement();
        // % salida (PorcAlmSanaSl): el frpond guardado en la linea (V32); las lineas anteriores no lo
        // tienen y se imprimieron con el factor de su entrada origen.
        BigDecimal exitPercentage = line.getExitPercentage() != null
                ? line.getExitPercentage() : origin.getHealthyPercentage();
        return new Sortable(line.getRemission().getCreatedAt(), 1, line.getId(), new InventoryReportRow(
                line.getRemission().getRemissionDate(), null, line.getRemission().getDisplayNumber(),
                origin.getProductCode().getCode(), origin.getSpecialType(), null, null, null,
                line.getQuantity(), exitPercentage, line.getOutputValue(), null));
    }

    private record Sortable(Instant createdAt, int kind, Long id, InventoryReportRow row) {
    }
}
