package com.cafeoccidente.backend.purchases.drycoffee.dto;

import com.cafeoccidente.backend.purchases.shared.dto.PurchasePaymentRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

/**
 * Solo lo que digita el cajero (Cedula, Sacos, Destare, pesos de muestra, Castigo, descuentos).
 * Factura, Pr Sustentacion, Bonificacion, Costos, Pr_AlmDefec y los datos del vendedor los resuelve
 * el servidor (anuncio vigente, consecutivo y TERCEROS); si el cliente los manda, se ignoran.
 * address y cellphone solo se usan cuando el tercero los tiene vacios.
 */
public record DryCoffeePurchaseRequest(
        @NotNull Long fundId,
        @NotBlank String specialType,
        @NotBlank String idNumber,
        String address,
        String cellphone,
        @NotNull @Positive Integer bagsCount,
        @NotNull @PositiveOrZero BigDecimal grossKg,
        @NotNull @PositiveOrZero BigDecimal tareKg,
        @NotNull @PositiveOrZero BigDecimal totalStoredWeight,
        @NotNull @PositiveOrZero BigDecimal defectiveStoredWeight,
        @NotNull @PositiveOrZero BigDecimal healthyStoredWeight,
        @NotNull @PositiveOrZero BigDecimal penalty,
        @NotNull @PositiveOrZero BigDecimal freightDiscount,
        @NotNull @PositiveOrZero BigDecimal otherDiscounts,
        /** Panel FORMAS DE PAGO; solo create() lo exige (preview calcula antes de que se llene). */
        @Valid PurchasePaymentRequest payment) {
}
