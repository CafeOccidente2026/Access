package com.cafeoccidente.backend.history.controller;

import com.cafeoccidente.backend.common.security.Roles;
import com.cafeoccidente.backend.history.dto.ChangeHistoryFilter;
import com.cafeoccidente.backend.history.dto.ChangeHistoryPage;
import com.cafeoccidente.backend.history.service.ChangeHistoryService;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Pantalla Historial (Acceso Principal), solo para el Administrador. */
@RestController
@RequestMapping("/api/history")
@PreAuthorize(Roles.ADMIN)
public class ChangeHistoryController {

    private static final int MAX_PAGE_SIZE = 200;

    private final ChangeHistoryService service;

    public ChangeHistoryController(ChangeHistoryService service) {
        this.service = service;
    }

    @GetMapping
    public ChangeHistoryPage search(
            @RequestParam(required = false) String entity,
            @RequestParam(required = false) Long agencyId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return service.search(new ChangeHistoryFilter(entity, agencyId, userId, from, to,
                Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE)));
    }
}
