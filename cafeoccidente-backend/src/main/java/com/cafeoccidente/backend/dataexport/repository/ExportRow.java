package com.cafeoccidente.backend.dataexport.repository;

import java.util.List;

/** Una fila de hoja de Excel y la fila de origen que se marca como exportada (table + id). */
public record ExportRow(String table, Long id, List<Object> values) {
}
