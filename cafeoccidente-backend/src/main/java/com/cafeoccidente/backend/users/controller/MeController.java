package com.cafeoccidente.backend.users.controller;

import com.cafeoccidente.backend.common.security.Roles;
import com.cafeoccidente.backend.users.dto.MeResponse;
import com.cafeoccidente.backend.users.dto.WorkingAgencyRequest;
import com.cafeoccidente.backend.users.service.WorkingAgencyService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Sesion actual y boton Agencias del menu principal (RF-05.1). */
@RestController
@RequestMapping("/api/me")
@PreAuthorize(Roles.ANY)
public class MeController {

    private final WorkingAgencyService workingAgencyService;

    public MeController(WorkingAgencyService workingAgencyService) {
        this.workingAgencyService = workingAgencyService;
    }

    @GetMapping
    public MeResponse me() {
        return workingAgencyService.me();
    }

    /** RN-05.2: el boton Agencias es solo del Administrador. */
    @PutMapping("/working-agency")
    @PreAuthorize(Roles.ADMIN)
    public MeResponse chooseWorkingAgency(@Valid @RequestBody WorkingAgencyRequest request) {
        return workingAgencyService.choose(request.agencyId());
    }
}
