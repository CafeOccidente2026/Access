package com.cafeoccidente.backend.purchases.greencoffee.dto;

import com.cafeoccidente.backend.purchases.shared.dto.PurchasePaymentRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

/**
 * Solo campos de entrada manual (Cedula, Sacos, Destare, Vr_Kilo_Comp, Castigo, etc). specialType
 * siempre es "CV" (fijo, ver GreenCoffeePurchase). Los calculados (greenKg, netKg, unitPrice,
 * grossValue, withholding, netToPay) los calcula el servidor (GreenCoffeePurchaseCalculator).
 */
public record GreenCoffeePurchaseRequest(
        @NotNull Long agencyId,
        @NotNull Long fundId,
        @NotNull Integer invoiceNumber,
        @NotBlank String idNumber,
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotBlank String growerType,
        @NotBlank String address,
        @NotBlank String cellphone,
        @NotNull @Positive Integer bagsCount,
        @NotNull @PositiveOrZero BigDecimal grossKg,
        @NotNull @PositiveOrZero BigDecimal tareKg,
        @NotNull @PositiveOrZero BigDecimal healthyUnitPrice,
        @NotNull @PositiveOrZero BigDecimal bonus,
        @NotNull @PositiveOrZero BigDecimal costs,
        @NotNull @PositiveOrZero BigDecimal penalty,
        @NotNull @PositiveOrZero BigDecimal compKgPrice,
        /** Lo ignora el servidor (se reemplaza en withWithholdingExempt); opcional en el JSON. */
        Boolean withholdingExempt,
        @NotNull @PositiveOrZero BigDecimal shrinkageDiscount,
        @NotNull @PositiveOrZero BigDecimal otherDiscounts,
        /** Panel FORMAS DE PAGO; solo create() lo exige (preview calcula antes de que se llene). */
        @Valid PurchasePaymentRequest payment) {
    /** Asociados.Asociacion: la exencion de Retefuente la resuelve el servidor (GrowerService.isAssociation),
     *  nunca la que mande el formulario - Form_COMPRAS.bas: If ... And Asociacion.Value = 0 Then Retefuente. */
    public GreenCoffeePurchaseRequest withWithholdingExempt(boolean exempt) {
        return new GreenCoffeePurchaseRequest(
                agencyId, fundId, invoiceNumber, idNumber, firstName, lastName, growerType, address, cellphone,
                bagsCount, grossKg, tareKg, healthyUnitPrice, bonus, costs, penalty, compKgPrice, exempt,
                shrinkageDiscount, otherDiscounts, payment);
    }
}
