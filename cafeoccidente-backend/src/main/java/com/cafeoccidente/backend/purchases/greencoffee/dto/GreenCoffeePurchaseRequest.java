package com.cafeoccidente.backend.purchases.greencoffee.dto;

import com.cafeoccidente.backend.purchases.shared.dto.PurchasePaymentRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

/**
 * Solo lo que digita el cajero (Cedula, Sacos, Destare, Vr_Kilo_Comp, Castigo, descuentos).
 * specialType siempre es "CV" (fijo, ver GreenCoffeePurchase). Factura, Pr Alm Sana, Bonificacion,
 * Costos y los datos del vendedor los resuelve el servidor (anuncio CV vigente, consecutivo y
 * TERCEROS); si el cliente los manda, se ignoran. address y cellphone solo se usan cuando el
 * tercero los tiene vacios.
 */
public record GreenCoffeePurchaseRequest(
        @NotNull Long fundId,
        @NotBlank String idNumber,
        String address,
        String cellphone,
        @NotNull @Positive Integer bagsCount,
        @NotNull @PositiveOrZero BigDecimal grossKg,
        @NotNull @PositiveOrZero BigDecimal tareKg,
        @NotNull @PositiveOrZero BigDecimal penalty,
        @NotNull @PositiveOrZero BigDecimal compKgPrice,
        @NotNull @PositiveOrZero BigDecimal shrinkageDiscount,
        @NotNull @PositiveOrZero BigDecimal otherDiscounts,
        /** Panel FORMAS DE PAGO; solo create() lo exige (preview calcula antes de que se llene). */
        @Valid PurchasePaymentRequest payment) {
}
