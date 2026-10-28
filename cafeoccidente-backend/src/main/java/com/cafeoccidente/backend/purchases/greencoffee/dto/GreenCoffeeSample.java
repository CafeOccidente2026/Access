package com.cafeoccidente.backend.purchases.greencoffee.dto;

import java.math.BigDecimal;

/**
 * Muestra fija de cafe verde (HU-23 CA-2): los 3 pesos del Registro de Control y los porcentajes que
 * salen de ellos con las formulas de cafe seco. Ej. 140 / 12,5 / 186,18 -> 44 % / 5 % / 94.
 */
public record GreenCoffeeSample(
        BigDecimal totalStoredWeight,
        BigDecimal wastePercentage,
        BigDecimal huskWeight,
        BigDecimal huskPercentage,
        BigDecimal healthyStoredWeight,
        BigDecimal factor) {
}
