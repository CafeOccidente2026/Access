package com.cafeoccidente.backend.annulmentlog.dto;

import java.time.LocalDate;

/** Filtros de Consultar anulaciones; un valor null no filtra. from y to son dias completos de Colombia. */
public record AnnulmentLogFilter(
        Long agencyId,
        String documentType,
        Long userId,
        LocalDate from,
        LocalDate to,
        int page,
        int size) {
}
