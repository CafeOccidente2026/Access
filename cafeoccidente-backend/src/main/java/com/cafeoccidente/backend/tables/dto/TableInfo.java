package com.cafeoccidente.backend.tables.dto;

import java.util.List;

/**
 * Una tabla que se puede consultar en Acceso a Tablas, con sus columnas visibles. {@code byAgency}: tiene
 * agencia_id y se filtra por agencia (RN-03.2); las demas se ven completas.
 */
public record TableInfo(String name, boolean byAgency, String primaryKey, List<ColumnInfo> columns) {

    /** El tipo decide el filtro: TEXT "contiene"; NUMBER y DATE tambien "desde / hasta". */
    public enum Kind { TEXT, NUMBER, DATE, BOOLEAN }

    public record ColumnInfo(String name, Kind kind) {
    }

    public ColumnInfo column(String name) {
        return columns.stream().filter(c -> c.name().equals(name)).findFirst().orElse(null);
    }
}
