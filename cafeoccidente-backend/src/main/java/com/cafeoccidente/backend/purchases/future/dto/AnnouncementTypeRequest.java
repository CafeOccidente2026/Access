package com.cafeoccidente.backend.purchases.future.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

/**
 * Listado "Actualizar" (RN-08.5, RN-08.6): un solo tipo de cafe con su precio, su bonificacion (SobrePr
 * Carga Esp) y su precio de pasilla. Sin precio, se usa el vigente del tipo.
 */
public record AnnouncementTypeRequest(
        @NotNull Long fundId,
        @NotBlank String specialType,
        @Positive BigDecimal basePriceLoad,
        @NotNull @PositiveOrZero BigDecimal specialSurcharge,
        @NotNull @PositiveOrZero BigDecimal defectiveUnitPrice) {
}
