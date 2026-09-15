package com.cafeoccidente.backend.purchases.husk.service;

import java.math.BigDecimal;

/** Resultado de la cascada de calculo, listo para volcarse en la entidad. */
public record HuskPurchaseCalculation(
        BigDecimal netKg,
        BigDecimal almondPercentage,
        BigDecimal unitPrice,
        BigDecimal grossValue,
        BigDecimal inventoryValue,
        BigDecimal associateContribution,
        BigDecimal cooperativeDiscount,
        BigDecimal withholding,
        BigDecimal netToPay) {
}
