package com.cafeoccidente.backend.inventory.service;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.inventory.dto.InventoryReportRow;
import com.cafeoccidente.backend.inventory.dto.ProductCodeOption;
import com.cafeoccidente.backend.inventory.entity.InventoryMovement;
import com.cafeoccidente.backend.inventory.entity.RemissionLine;
import com.cafeoccidente.backend.inventory.repository.InventoryMovementRepository;
import com.cafeoccidente.backend.inventory.repository.RemissionLineRepository;
import com.cafeoccidente.backend.purchases.shared.repository.ProductCodeRepository;
import java.math.BigDecimal;
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

        // Corte por año, en un solo lugar: al cerrar el año Access borraba las filas del año anterior
        // (consulta "eliminainventarioañoanterior": DELETE FROM INVENTARIO WHERE Fecha Between
        // #1/1/2025# And #12/31/2025#), asi que el reporte solo ve el año en curso y arranca en cero.
        LocalDate from = today.withDayOfYear(1);
        LocalDate to = today.withDayOfYear(today.lengthOfYear());

        List<Sortable> rows = new ArrayList<>();
        inventoryMovementRepository.findByAgencyIdAndPurchaseDateBetween(agencyId, from, to).stream()
                .filter(matches)
                .forEach(m -> rows.add(new Sortable(m.getCreatedAt(), 0, m.getId(), new InventoryReportRow(
                        m.getPurchaseDate(), m.getInvoiceNumber(), null, m.getProductCode().getCode(),
                        m.getSpecialType(), m.getNetKg(), m.getHealthyPercentage(), m.getInventoryValue(),
                        null, null, null))));
        remissionLineRepository.findByRemissionAgencyIdAndRemissionRemissionDateBetween(agencyId, from, to).stream()
                .filter(line -> matches.test(line.getInventoryMovement()))
                .forEach(line -> rows.add(exitRow(line)));

        Comparator<Sortable> order = byCode
                // INVXCODPROD: BreakLevel Id = orden de ingreso a la tabla INVENTARIO.
                ? Comparator.comparing((Sortable s) -> s.createdAt()).thenComparing(s -> s.kind()).thenComparing(s -> s.id())
                // INVENTARIO: BreakLevel Fecha, Factura, Remision (ascendente, Null primero como Access).
                : Comparator.comparing((Sortable s) -> s.row().date())
                        .thenComparing(s -> s.row().invoiceNumber(), Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(s -> s.row().remissionNumber(), Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(s -> s.id());
        return rows.stream().sorted(order).map(s -> s.row()).toList();
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
                line.getQuantity(), exitPercentage, line.getOutputValue()));
    }

    private record Sortable(Instant createdAt, int kind, Long id, InventoryReportRow row) {
    }
}
