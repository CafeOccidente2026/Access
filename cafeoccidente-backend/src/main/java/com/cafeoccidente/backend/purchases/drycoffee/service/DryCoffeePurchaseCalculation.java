package com.cafeoccidente.backend.purchases.drycoffee.service;

import java.math.BigDecimal;

/** Resultado de la cascada de calculo, listo para volcarse en la entidad. */
public record DryCoffeePurchaseCalculation(
        BigDecimal netKg,
        BigDecimal wastePercentage,
        BigDecimal defectivePercentage,
        BigDecimal healthyPercentage,
        BigDecimal unitPrice,
        BigDecimal grossValue,
        BigDecimal inventoryValue,
        BigDecimal associateContribution,
        BigDecimal cooperativeDiscount,
        BigDecimal withholding,
        BigDecimal netToPay) {
}
