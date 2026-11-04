package com.cafeoccidente.backend.tables.dto;

import java.util.List;

/** Una pagina de una tabla, de solo lectura: nombres de columna, filas como texto y el total de filas. */
public record TablePage(List<String> columns, List<List<String>> rows, long total) {
}
