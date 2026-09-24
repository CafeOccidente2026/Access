package com.cafeoccidente.backend.purchases.future.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

/**
 * Los campos que llena el admin en "Actualizar Anuncio con Factor" (Pr_Base_CPS, Pr_AlmDefec,
 * SobrePr_CPS, Especial, Fondo). Agencia, Numero de Anuncio, Fecha, Costos, Pr_AlmSana y
 * Bonificacion los completa el servidor (ver Form_ANUNCIOS CORRF.bas / AnnouncementServiceImpl.create).
 */
public record AnnouncementRequest(
        @NotNull @PositiveOrZero BigDecimal basePriceLoad,
        @NotNull @PositiveOrZero BigDecimal defectiveUnitPrice,
        @NotNull @PositiveOrZero BigDecimal specialSurcharge,
        @NotBlank String specialType,
        @NotNull Long fundId,
        /** VrIncCalidad ("Pr IncCalidad") - opcional, 0 si no aplica para este anuncio. */
        @PositiveOrZero BigDecimal qualityIncrement) {
}
