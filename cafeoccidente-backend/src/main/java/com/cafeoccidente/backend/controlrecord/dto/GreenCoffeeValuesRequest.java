package com.cafeoccidente.backend.controlrecord.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

/** Los 5 valores de cafe verde del Registro de Control (RN-06.7) para aplicar a todas las agencias. */
public record GreenCoffeeValuesRequest(
        @NotNull @PositiveOrZero BigDecimal greenTotalStoredWeight,
        @NotNull @PositiveOrZero BigDecimal greenTotalHuskWeight,
        @NotNull @PositiveOrZero BigDecimal greenHealthyStoredWeight,
        @NotNull @PositiveOrZero BigDecimal greenHealthyUnitPrice,
        @NotNull @PositiveOrZero BigDecimal greenDefectiveUnitPrice) {
}
