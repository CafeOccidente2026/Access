package com.cafeoccidente.backend.purchases.drycoffee.dto;

import com.cafeoccidente.backend.purchases.shared.dto.PurchasePaymentRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

/**
 * Solo lleva los campos de entrada manual del formulario (Cedula, Sacos, Destare, Castigo, etc).
 * Los campos calculados (netKg, porcentajes, precios, Retefuente, netToPay, productCode) los
 * calcula el servidor via DryCoffeePurchaseCalculator - nunca se reciben del cliente.
 * Pr_AlmDefec (precio almendra defectuosa) tampoco viene del cliente: lo trae el anuncio vigente
 * (ver Announcement.defectiveUnitPrice), igual que basePriceLoad.
 */
public record DryCoffeePurchaseRequest(
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
        /** Lo ignora el servidor (se reemplaza en withWithholdingExempt); opcional en el JSON. */
        Boolean withholdingExempt,
        @NotNull @PositiveOrZero BigDecimal freightDiscount,
        @NotNull @PositiveOrZero BigDecimal otherDiscounts,
        /** Panel FORMAS DE PAGO; solo create() lo exige (preview calcula antes de que se llene). */
        @Valid PurchasePaymentRequest payment) {
    /** Asociados.Asociacion: la exencion de Retefuente la resuelve el servidor (GrowerService.isAssociation),
     *  nunca la que mande el formulario - Form_COMPRAS.bas: If ... And Asociacion.Value = 0 Then Retefuente. */
    public DryCoffeePurchaseRequest withWithholdingExempt(boolean exempt) {
        return new DryCoffeePurchaseRequest(
                agencyId, fundId, invoiceNumber, specialType, idNumber, firstName, lastName, growerType,
                address, cellphone, bagsCount, grossKg, tareKg, totalStoredWeight, defectiveStoredWeight,
                healthyStoredWeight, healthyUnitPrice, bonus, penalty, costs, exempt, freightDiscount,
                otherDiscounts, payment);
    }
}
