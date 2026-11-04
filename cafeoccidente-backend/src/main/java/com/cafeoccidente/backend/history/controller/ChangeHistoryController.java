package com.cafeoccidente.backend.history.controller;

import com.cafeoccidente.backend.common.security.CurrentAgencyProvider;
import com.cafeoccidente.backend.common.security.Roles;
import com.cafeoccidente.backend.common.security.SecurityUtils;
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

/**
 * Pantalla Historial (Acceso Principal). El Administrador ve todas las agencias; quien tiene el permiso
 * (RF-02), solo los cambios de la suya.
 */
@RestController
@RequestMapping("/api/history")
@PreAuthorize(Roles.HISTORY)
public class ChangeHistoryController {

    private static final int MAX_PAGE_SIZE = 200;

    private final ChangeHistoryService service;
    private final SecurityUtils securityUtils;
    private final CurrentAgencyProvider currentAgencyProvider;

    public ChangeHistoryController(ChangeHistoryService service, SecurityUtils securityUtils,
            CurrentAgencyProvider currentAgencyProvider) {
        this.service = service;
        this.securityUtils = securityUtils;
        this.currentAgencyProvider = currentAgencyProvider;
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
        Long agency = securityUtils.isAdmin() ? agencyId : currentAgencyProvider.currentAgencyId();
        return service.search(new ChangeHistoryFilter(entity, agency, userId, from, to,
                Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE)));
    }
}
