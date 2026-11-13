package com.cafeoccidente.backend.tables.controller;

import com.cafeoccidente.backend.common.security.Roles;
import com.cafeoccidente.backend.tables.dto.TableInfo;
import com.cafeoccidente.backend.tables.dto.TablePage;
import com.cafeoccidente.backend.tables.service.TableAccessService;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Pantalla "Acceso a Tablas" (Acceso Principal, RF-03): el Administrador o quien tenga el permiso (RF-02).
 * Solo lectura (RN-03.3): este controlador no tiene ningun verbo de escritura.
 */
@RestController
@RequestMapping("/api/tables")
@PreAuthorize(Roles.TABLES)
public class TableAccessController {

    private final TableAccessService service;

    public TableAccessController(TableAccessService service) {
        this.service = service;
    }

    @GetMapping
    public List<TableInfo> tables() {
        return service.tables();
    }

    /** Hoja de datos: offset, limit, sort, dir, q, agency y filtros f.columna, f.columna.from y f.columna.to. */
    @GetMapping("/{table}")
    public TablePage rows(@PathVariable String table, @RequestParam Map<String, String> params) {
        return service.rows(table, params);
    }
}
