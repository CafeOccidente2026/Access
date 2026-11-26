package com.cafeoccidente.backend.annulmentlog.dto;

import java.time.LocalDate;

/**
 * Filtros de Consultar anulaciones; un valor null no filtra. from y to son dias completos de Colombia. Las de Access
 * no tienen fecha de anulacion: con includeAccess salen sin mirar from y to; sin el, no salen.
 */
public record AnnulmentLogFilter(
        Long agencyId,
        String documentType,
        Long userId,
        LocalDate from,
        LocalDate to,
        boolean includeAccess,
        int page,
        int size) {
}
