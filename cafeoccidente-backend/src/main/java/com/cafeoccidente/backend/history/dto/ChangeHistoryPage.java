package com.cafeoccidente.backend.history.dto;

import java.util.List;

/** Una pagina del historial (mas reciente primero) y el total de filas que cumplen los filtros. */
public record ChangeHistoryPage(List<ChangeHistoryEntry> items, long total) {
}
