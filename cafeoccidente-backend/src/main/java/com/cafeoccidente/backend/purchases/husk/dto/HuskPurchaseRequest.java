package com.cafeoccidente.backend.purchases.husk.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

/**
 * Solo campos de entrada manual (Cedula, Sacos, Destare, Peso Almendra, etc). specialType siempre
 * es "PASILLA" (fijo). Los calculados (netKg, almondPercentage, unitPrice, grossValue, withholding,
 * netToPay) los calcula el servidor (HuskPurchaseCalculator).
 */
public record HuskPurchaseRequest(
        @NotNull Long agencyId,
        @NotNull Long fundId,
        @NotNull Integer invoiceNumber,
        @NotBlank String idNumber,
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotBlank String growerType,
        @NotBlank String address,
        @NotBlank String cellphone,
        @NotNull @PositiveOrZero BigDecimal almondWeight,
        @NotNull @Positive Integer bagsCount,
        @NotNull @PositiveOrZero BigDecimal grossKg,
        @NotNull @PositiveOrZero BigDecimal tareKg,
        @NotNull @PositiveOrZero BigDecimal pointPrice,
        @NotNull @PositiveOrZero BigDecimal costs,
        boolean withholdingExempt,
        @NotNull @PositiveOrZero BigDecimal shrinkageDiscount,
        @NotNull @PositiveOrZero BigDecimal otherDiscounts,
        @NotBlank String paymentMethod,
        String checkNumber) {
}
