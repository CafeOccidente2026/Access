package com.cafeoccidente.backend.tables.service;

import com.cafeoccidente.backend.tables.dto.TablePage;
import com.cafeoccidente.backend.tables.entity.QueryableTable;
import java.util.List;

/** Acceso a Tablas (RF-03): lista y consulta de solo lectura, filtrada por la agencia de trabajo. */
public interface TableAccessService {

    /** RN-03.1. */
    List<QueryableTable> tables();

    /** RN-03.2 y RN-03.3. Un codigo que no esta en la lista da 404. */
    TablePage page(String code, int page, int size);
}
