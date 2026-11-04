package com.cafeoccidente.backend.supplies.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Fila comun de los informes de Suministros. balance = Saldo (RunningSum de Entradas - Salidas) en
 * los informes de movimiento; null en Relacion Cheques y Forma de Pago, que no lo tienen.
 */
public record SuppliesReportRow(
        String agencyName,
        String transactionId,
        String fundCode,
        LocalDate date,
        String idNumber,
        String firstNames,
        String lastNames,
        String detail,
        BigDecimal inflow,
        BigDecimal outflow,
        BigDecimal balance,
        String paymentMethod,
        Integer checkNumber) {
}
