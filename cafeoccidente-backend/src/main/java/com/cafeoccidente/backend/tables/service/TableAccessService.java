package com.cafeoccidente.backend.tables.service;

import com.cafeoccidente.backend.tables.dto.TableInfo;
import com.cafeoccidente.backend.tables.dto.TablePage;
import java.util.List;
import java.util.Map;

/** Acceso a Tablas (RF-03): las tablas que ve el usuario y sus filas, de solo lectura. */
public interface TableAccessService {

    List<TableInfo> tables();

    /**
     * Filas de la tabla segun la hoja de datos: {@code params} trae offset, limit, sort, dir, q, agency y los filtros
     * f.columna (contiene), f.columna.from y f.columna.to (rango). Todo se valida contra el catalogo.
     */
    TablePage rows(String table, Map<String, String> params);
}
