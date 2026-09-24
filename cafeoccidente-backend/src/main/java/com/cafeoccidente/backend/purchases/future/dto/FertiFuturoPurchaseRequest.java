package com.cafeoccidente.backend.purchases.future.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

/**
 * Campos de entrada de FERTIFUTURO. specialType usa las etiquetas propias de ese formulario
 * (RN/NESS/CORR/TE) - "NESS" resuelve al mismo Cod_Prod que "NESPRESSO - FTUSA" en Seco/Otros (ver
 * FertiFuturoPurchase). netKg/grossKg son inputs directos (a diferencia de Seco, donde netKg se
 * deriva) - tareKg lo calcula el servidor como grossKg - netKg (Kilos_Brutos_AfterUpdate).
 */
public record FertiFuturoPurchaseRequest(
        @NotNull Long agencyId,
        @NotNull Long fundId,
        @NotNull Integer invoiceNumber,
        @NotBlank String specialType,
        @NotBlank String idNumber,
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotBlank String growerType,
        @NotBlank String address,
        /** Compromiso de Compras a Futuro que esta liquidacion paga, si aplica (opcional). */
        Long futurePurchaseId,
        @NotNull @Positive Integer sacos,
        @NotNull @PositiveOrZero BigDecimal netKg,
        @NotNull @PositiveOrZero BigDecimal grossKg,
        @NotNull @PositiveOrZero BigDecimal healthyStoredWeight,
        @NotNull @PositiveOrZero BigDecimal defectiveStoredWeight,
        @NotNull @PositiveOrZero BigDecimal penalty,
        boolean withholdingExempt,
        @NotNull @PositiveOrZero BigDecimal freightDiscount,
        @NotNull @PositiveOrZero BigDecimal otherDiscounts,
        @NotBlank String paymentMethod,
        String checkNumber) {
}
