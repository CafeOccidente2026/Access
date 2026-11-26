package com.cafeoccidente.backend.annulmentlog.dto;

import java.util.List;

/** Una pagina de Consultar anulaciones y el total con esos filtros. */
public record AnnulmentLogPage(List<AnnulmentLogEntry> items, long total) {
}
