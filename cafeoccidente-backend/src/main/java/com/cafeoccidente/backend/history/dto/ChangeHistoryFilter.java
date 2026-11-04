package com.cafeoccidente.backend.history.dto;

import java.time.LocalDate;

/** Filtros de la pantalla Historial; todos opcionales. from/to son dias completos. */
public record ChangeHistoryFilter(
        String entity, Long agencyId, Long userId, LocalDate from, LocalDate to, int page, int size) {
}
