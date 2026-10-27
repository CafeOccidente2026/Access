package com.cafeoccidente.backend.purchases.drycoffee.dto;

import java.math.BigDecimal;

/**
 * Porcentajes calculados en los pasos "Peso Tot Alm"/"Peso Tot Pasilla"/"Peso Alm Sana", antes de
 * conocer los demas campos de la cascada completa (ver DryCoffeePurchaseCalculator). Cada campo
 * viene null si el peso correspondiente no se envio en la consulta.
 */
/** healthyStoredWeight y healthyPercentage (Factor) solo vienen cuando llegan Peso Tot Alm y Peso Pasilla (Q2). */
public record QualityPercentagesResponse(
        BigDecimal wastePercentage, BigDecimal defectivePercentage, BigDecimal healthyStoredWeight,
        BigDecimal healthyPercentage) {
}
