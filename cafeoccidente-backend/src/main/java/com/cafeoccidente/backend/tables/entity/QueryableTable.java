package com.cafeoccidente.backend.tables.entity;

/**
 * Tablas que se pueden consultar en "Acceso a Tablas" (RF-03): las del formulario Tablas de Access,
 * con Compras partida en las tres tablas de compras de la base nueva. Es la lista blanca: el nombre real
 * de la tabla nunca viene del cliente.
 */
public enum QueryableTable {
    CAJA_MENOR("movimientos_caja_menor", true),
    COMPRAS_CAFE_SECO("compras_cafe_seco", true),
    COMPRAS_CAFE_VERDE("compras_cafe_verde", true),
    COMPRAS_PASILLA("compras_pasilla", true),
    COMPRAS_A_FUTURO("compras_anunciadas", true),
    INVENTARIO("inventarios", true),
    /** Comun a todas las agencias: se muestra completa. */
    PRODUCTOS("productos", false),
    EMPAQUE("empaques", true),
    SUMINISTROS("suministros", true),
    CAJA("movimientos_caja", true);

    private final String tableName;
    private final boolean byAgency;

    QueryableTable(String tableName, boolean byAgency) {
        this.tableName = tableName;
        this.byAgency = byAgency;
    }

    public String getTableName() {
        return tableName;
    }

    /** RN-03.2: se filtra por la agencia de trabajo. */
    public boolean isByAgency() {
        return byAgency;
    }
}
