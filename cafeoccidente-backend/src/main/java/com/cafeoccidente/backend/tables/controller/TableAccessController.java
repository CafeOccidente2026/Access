package com.cafeoccidente.backend.tables.controller;

import com.cafeoccidente.backend.common.security.Roles;
import com.cafeoccidente.backend.tables.dto.TablePage;
import com.cafeoccidente.backend.tables.entity.QueryableTable;
import com.cafeoccidente.backend.tables.service.TableAccessService;
import java.util.List;
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

    private static final int MAX_PAGE_SIZE = 200;

    private final TableAccessService service;

    public TableAccessController(TableAccessService service) {
        this.service = service;
    }

    @GetMapping
    public List<QueryableTable> tables() {
        return service.tables();
    }

    @GetMapping("/{code}")
    public TablePage page(@PathVariable String code,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        return service.page(code, Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE));
    }
}
