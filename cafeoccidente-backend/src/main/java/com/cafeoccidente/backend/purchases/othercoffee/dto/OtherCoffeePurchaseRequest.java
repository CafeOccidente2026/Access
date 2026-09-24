package com.cafeoccidente.backend.purchases.othercoffee.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

/**
 * Solo los campos de entrada manual del formulario "Cafés Otros" (COMPRASESP). Igual forma que
 * DryCoffeePurchaseRequest - fundId aca es real (RP o LF, Cuadro combinado37), no fijo como en
 * Verde/Pasilla.
 */
public record OtherCoffeePurchaseRequest(
        @NotNull Long agencyId,
        @NotNull Long fundId,
        @NotNull Integer invoiceNumber,
        @NotBlank String specialType,
        @NotBlank String idNumber,
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotBlank String growerType,
        @NotBlank String address,
        @NotBlank String cellphone,
        @NotNull @Positive Integer bagsCount,
        @NotNull @PositiveOrZero BigDecimal grossKg,
        @NotNull @PositiveOrZero BigDecimal tareKg,
        @NotNull @PositiveOrZero BigDecimal totalStoredWeight,
        @NotNull @PositiveOrZero BigDecimal defectiveStoredWeight,
        @NotNull @PositiveOrZero BigDecimal healthyStoredWeight,
        @NotNull @PositiveOrZero BigDecimal healthyUnitPrice,
        @NotNull @PositiveOrZero BigDecimal bonus,
        @NotNull @PositiveOrZero BigDecimal penalty,
        @NotNull @PositiveOrZero BigDecimal costs,
        boolean withholdingExempt,
        @NotNull @PositiveOrZero BigDecimal freightDiscount,
        @NotNull @PositiveOrZero BigDecimal otherDiscounts,
        @NotBlank String paymentMethod,
        String checkNumber) {
}
