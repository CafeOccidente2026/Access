package com.cafeoccidente.backend.purchases.future.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/** Cupo a asignar (kg totales) a un anuncio ya existente de la agencia. */
public record AnnouncementQuotaRequest(@NotNull @Positive BigDecimal assignedQuota) {
}
