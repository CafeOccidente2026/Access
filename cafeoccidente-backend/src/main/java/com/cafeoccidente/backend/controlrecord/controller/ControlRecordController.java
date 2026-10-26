package com.cafeoccidente.backend.controlrecord.controller;

import com.cafeoccidente.backend.common.security.CurrentAgencyProvider;
import com.cafeoccidente.backend.common.security.Roles;
import com.cafeoccidente.backend.common.security.SecurityUtils;
import com.cafeoccidente.backend.controlrecord.dto.ControlRecordRequest;
import com.cafeoccidente.backend.controlrecord.dto.ControlRecordResponse;
import com.cafeoccidente.backend.controlrecord.dto.GreenCoffeeAppliedResponse;
import com.cafeoccidente.backend.controlrecord.dto.GreenCoffeeValuesRequest;
import com.cafeoccidente.backend.controlrecord.service.ControlRecordService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Pantalla "Registro de Control" (RegControl): ver/crear/editar los parametros por agencia. El
 * Administrador en cualquier agencia; quien tiene el permiso (RF-02), solo en la suya.
 */
@RestController
@RequestMapping("/api/control-records")
@PreAuthorize(Roles.CONTROL_RECORD)
public class ControlRecordController {

    private final ControlRecordService controlRecordService;
    private final SecurityUtils securityUtils;
    private final CurrentAgencyProvider currentAgencyProvider;

    public ControlRecordController(ControlRecordService controlRecordService, SecurityUtils securityUtils,
            CurrentAgencyProvider currentAgencyProvider) {
        this.controlRecordService = controlRecordService;
        this.securityUtils = securityUtils;
        this.currentAgencyProvider = currentAgencyProvider;
    }

    @GetMapping
    @PreAuthorize(Roles.ADMIN)
    public List<ControlRecordResponse> list() {
        return controlRecordService.list();
    }

    @GetMapping("/{agencyId}")
    public ControlRecordResponse getByAgency(@PathVariable Long agencyId) {
        return controlRecordService.getByAgency(allowed(agencyId));
    }

    @PostMapping("/{agencyId}")
    @ResponseStatus(HttpStatus.CREATED)
    public ControlRecordResponse create(@PathVariable Long agencyId, @Valid @RequestBody ControlRecordRequest request) {
        return controlRecordService.create(allowed(agencyId), request);
    }

    @PutMapping("/{agencyId}")
    public ControlRecordResponse update(@PathVariable Long agencyId, @Valid @RequestBody ControlRecordRequest request) {
        return controlRecordService.update(allowed(agencyId), request);
    }

    /**
     * Cafe verde en todas las agencias a la vez (area de compras, 2026-10-26). Solo el Administrador: el
     * permiso de la pantalla (RF-02) esta limitado a la propia agencia.
     */
    @PutMapping("/green-coffee")
    @PreAuthorize(Roles.ADMIN)
    public GreenCoffeeAppliedResponse applyGreenCoffeeToAll(@Valid @RequestBody GreenCoffeeValuesRequest request) {
        return controlRecordService.applyGreenCoffeeToAll(request);
    }

    /** Con el permiso de la pantalla (no Administrador), solo la propia agencia (RN-01.3). */
    private Long allowed(Long agencyId) {
        if (!securityUtils.isAdmin() && !agencyId.equals(currentAgencyProvider.currentAgencyId())) {
            throw new AccessDeniedException("Solo puede ver el Registro de Control de su agencia");
        }
        return agencyId;
    }
}
