package com.cafeoccidente.backend.purchases.othercoffee.dto;

import java.math.BigDecimal;

public record QualityPercentagesResponse(
        BigDecimal wastePercentage, BigDecimal defectivePercentage, BigDecimal healthyPercentage) {
}
