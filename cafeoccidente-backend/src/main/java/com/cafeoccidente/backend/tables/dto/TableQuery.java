package com.cafeoccidente.backend.tables.dto;

import java.util.List;

/**
 * Lo que pide la hoja de datos, ya validado contra el catalogo: tramo de filas, orden, busqueda en todas las
 * columnas y filtros por columna. {@code agencyId} null = todas las agencias.
 */
public record TableQuery(Long agencyId, int offset, int limit, String sort, boolean ascending, String search,
        List<Filter> filters) {

    /** contains: texto que contiene; from / to: rango para numeros y fechas. */
    public record Filter(TableInfo.ColumnInfo column, Operator operator, Object value) {
    }

    public enum Operator { CONTAINS, FROM, TO }
}
