package com.cafeoccidente.backend.history.dto;

import java.time.OffsetDateTime;
import java.util.Map;

/** Una fila del historial: quien, cuando, que entidad y los campos que cambiaron. */
public record ChangeHistoryEntry(
        Long id,
        OffsetDateTime at,
        String username,
        String entity,
        Long entityId,
        String action,
        String agencyName,
        Map<String, String> before,
        Map<String, String> after) {
}
