package com.cafeoccidente.backend.purchases.future.service;

import java.math.BigDecimal;

public record FertiFuturoPurchaseCalculation(
        BigDecimal tareKg,
        BigDecimal healthyPercentage,
        BigDecimal defectivePercentage,
        BigDecimal qualityIncrementAmount,
        BigDecimal unitPrice,
        BigDecimal grossValue,
        BigDecimal inventoryValue,
        BigDecimal associateContribution,
        BigDecimal cooperativeDiscount,
        BigDecimal withholding,
        BigDecimal netToPay) {
}
