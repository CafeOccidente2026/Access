package com.cafeoccidente.backend.controlrecord.dto;

import java.time.LocalDate;

/** R1 (17-nov): una agencia cuya resolucion DIAN vence en {@code daysLeft} dias (negativo: ya vencio). */
public record ResolutionAlertResponse(String agencyName, LocalDate expirationDate, long daysLeft) {
}
