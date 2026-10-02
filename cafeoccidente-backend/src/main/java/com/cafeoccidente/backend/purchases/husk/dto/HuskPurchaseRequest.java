package com.cafeoccidente.backend.purchases.husk.dto;

import com.cafeoccidente.backend.purchases.shared.dto.PurchasePaymentRequest;
import jakarta.validation.Valid;
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
        /** Lo ignora el servidor (se reemplaza en withWithholdingExempt); opcional en el JSON. */
        Boolean withholdingExempt,
        @NotNull @PositiveOrZero BigDecimal shrinkageDiscount,
        @NotNull @PositiveOrZero BigDecimal otherDiscounts,
        /** Panel FORMAS DE PAGO; solo create() lo exige (preview calcula antes de que se llene). */
        @Valid PurchasePaymentRequest payment) {
    /** Asociados.Asociacion: la exencion de Retefuente la resuelve el servidor (GrowerService.isAssociation),
     *  nunca la que mande el formulario - Form_COMPRAS.bas: If ... And Asociacion.Value = 0 Then Retefuente. */
    public HuskPurchaseRequest withWithholdingExempt(boolean exempt) {
        return new HuskPurchaseRequest(
                agencyId, fundId, invoiceNumber, idNumber, firstName, lastName, growerType, address, cellphone,
                almondWeight, bagsCount, grossKg, tareKg, pointPrice, costs, exempt, shrinkageDiscount,
                otherDiscounts, payment);
    }
}
