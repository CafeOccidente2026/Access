package com.cafeoccidente.backend.purchases.greencoffee.service;

import java.math.BigDecimal;

/** Resultado de la cascada de calculo, listo para volcarse en la entidad. */
public record GreenCoffeePurchaseCalculation(
        BigDecimal basePriceLoad,
        BigDecimal greenKg,
        BigDecimal netKg,
        BigDecimal unitPrice,
        BigDecimal grossValue,
        BigDecimal inventoryValue,
        BigDecimal associateContribution,
        BigDecimal cooperativeDiscount,
        BigDecimal withholding,
        BigDecimal netToPay) {
}
