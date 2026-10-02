package com.cafeoccidente.backend.purchases.shared.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

/**
 * Panel "FORMAS DE PAGO" de los formularios de compra de Access: FPef, FPch (+ NumCheque), FPtx y
 * FPdat, que se reparten el Neto a Pagar y tienen que sumarlo exacto.
 */
public record PurchasePaymentRequest(
        @NotNull @PositiveOrZero BigDecimal cashAmount,
        @NotNull @PositiveOrZero BigDecimal checkAmount,
        @NotNull @PositiveOrZero BigDecimal transferAmount,
        @NotNull @PositiveOrZero BigDecimal cardAmount,
        String checkNumber) {
}
