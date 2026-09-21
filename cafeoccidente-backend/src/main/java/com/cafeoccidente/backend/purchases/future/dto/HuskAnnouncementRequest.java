package com.cafeoccidente.backend.purchases.future.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

/**
 * Los dos campos que llena el admin en "Actualizar Anuncio Pasilla" (Pr_Base_CPS y Pr_AlmSana,
 * etiqueta real "Pr Punto" en Form_ANUNCIOS PASILLA.bas). Fondo (RP) y Especial (PASILLA) van
 * fijos; Agencia, Numero de Anuncio y Fecha los completa el servidor (ver AnnouncementServiceImpl).
 */
public record HuskAnnouncementRequest(
        @NotNull @PositiveOrZero BigDecimal basePriceLoad,
        @NotNull @PositiveOrZero BigDecimal pointPrice) {
}
