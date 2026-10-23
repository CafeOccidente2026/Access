package com.cafeoccidente.backend.purchases.shared.controller;

import com.cafeoccidente.backend.common.security.Roles;
import com.cafeoccidente.backend.purchases.shared.dto.AgencyRequest;
import com.cafeoccidente.backend.purchases.shared.dto.AgencyResponse;
import com.cafeoccidente.backend.purchases.shared.service.AgencyService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agencias, solo para el Administrador: las listan el boton Agencias (RF-05.1), Registro de Control y
 * Usuarios, y se crean desde Usuarios (HU-09, RN-07.4).
 */
@RestController
@RequestMapping("/api/agencies")
@PreAuthorize(Roles.ADMIN)
public class AgencyController {

    private final AgencyService agencyService;

    public AgencyController(AgencyService agencyService) {
        this.agencyService = agencyService;
    }

    @GetMapping
    public List<AgencyResponse> list() {
        return agencyService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AgencyResponse create(@Valid @RequestBody AgencyRequest request) {
        return agencyService.create(request);
    }
}
