package com.cafeoccidente.backend.controlrecord.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Campos editables del ControlRecord (pantalla "RegControl"). agencyId va en la URL, no aqui. */
public record ControlRecordRequest(
        @NotNull Integer controlNumber,
        @NotNull Integer baseFactor,
        @NotNull @PositiveOrZero BigDecimal baseWithholding,
        @NotNull Integer baseLoad,
        @NotNull @PositiveOrZero BigDecimal withholdingPercentage,
        @NotNull @PositiveOrZero BigDecimal baseHusk,
        @NotNull @PositiveOrZero BigDecimal avgHuskPercentage,
        @NotBlank String purchasePoint,
        @NotBlank String prefix,
        @NotNull @PositiveOrZero BigDecimal costs,
        @NotNull @PositiveOrZero BigDecimal sampleSize,
        @NotNull @PositiveOrZero BigDecimal excelsoKg,
        @NotNull @PositiveOrZero BigDecimal greenCoffeePercentage,
        @NotNull @PositiveOrZero BigDecimal specialtyThreshold,
        @NotNull @PositiveOrZero BigDecimal associatePercentage,
        @NotNull @PositiveOrZero BigDecimal nonAssociateDiscount,
        @NotBlank String trustedId,
        @NotBlank String dianResolution,
        @NotNull LocalDate resolutionDate,
        @NotNull Integer resolutionFrom,
        @NotNull Integer resolutionTo,
        @NotNull Integer validity) {
}
