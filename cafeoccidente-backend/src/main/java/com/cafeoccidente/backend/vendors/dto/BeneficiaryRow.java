package com.cafeoccidente.backend.vendors.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Consultas "Beneficiario" / "Beneficiario Resumen": COMPRAS INNER JOIN Asociados, campo a campo. */
public record BeneficiaryRow(
        String agencyName,
        LocalDate purchaseDate,
        String fundCode,
        Integer invoiceNumber,
        String idNumber,
        String firstName,
        String lastName,
        BigDecimal greenKg,
        BigDecimal netKg,
        BigDecimal healthyPercentage,
        BigDecimal grossValue,
        BigDecimal associateContribution,
        BigDecimal cooperativeDiscount,
        BigDecimal freightDiscount,
        BigDecimal withholding,
        BigDecimal otherDiscounts,
        BigDecimal netToPay,
        String specialType) {
}
