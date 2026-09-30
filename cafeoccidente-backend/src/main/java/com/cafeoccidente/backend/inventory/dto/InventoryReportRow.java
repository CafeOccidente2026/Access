package com.cafeoccidente.backend.inventory.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Una fila de los reportes INVENTARIO / INVXCODPROD: en Access la tabla INVENTARIO mezcla entradas
 * (Factura, Kilos_Netos, PorcAlmSana, Vr_Inventario) y salidas (Remision, Cantidad, PorcAlmSanaSl,
 * Vr_Salida). Aca una entrada es un InventoryMovement y una salida una RemissionLine; los campos del
 * otro tipo quedan en null. Los saldos (sumas corridas) los calcula el reporte, como en Access.
 */
public record InventoryReportRow(
        LocalDate date,
        Integer invoiceNumber,
        String remissionNumber,
        String productCode,
        String specialType,
        BigDecimal netKg,
        BigDecimal healthyPercentage,
        BigDecimal inventoryValue,
        BigDecimal quantity,
        BigDecimal exitPercentage,
        BigDecimal outputValue) {
}
