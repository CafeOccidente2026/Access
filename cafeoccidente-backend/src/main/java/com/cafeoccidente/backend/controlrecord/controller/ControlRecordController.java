package com.cafeoccidente.backend.controlrecord.controller;

import com.cafeoccidente.backend.controlrecord.dto.ControlRecordRequest;
import com.cafeoccidente.backend.controlrecord.dto.ControlRecordResponse;
import com.cafeoccidente.backend.controlrecord.service.ControlRecordService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Pantalla "Registro de Control" (RegControl): ver/crear/editar los parametros por agencia. Solo ADMIN. */
@RestController
@RequestMapping("/api/control-records")
@PreAuthorize("hasRole('ADMIN')")
public class ControlRecordController {

    private final ControlRecordService controlRecordService;

    public ControlRecordController(ControlRecordService controlRecordService) {
        this.controlRecordService = controlRecordService;
    }

    @GetMapping
    public List<ControlRecordResponse> list() {
        return controlRecordService.list();
    }

    @GetMapping("/{agencyId}")
    public ControlRecordResponse getByAgency(@PathVariable Long agencyId) {
        return controlRecordService.getByAgency(agencyId);
    }

    @PostMapping("/{agencyId}")
    @ResponseStatus(HttpStatus.CREATED)
    public ControlRecordResponse create(@PathVariable Long agencyId, @Valid @RequestBody ControlRecordRequest request) {
        return controlRecordService.create(agencyId, request);
    }

    @PutMapping("/{agencyId}")
    public ControlRecordResponse update(@PathVariable Long agencyId, @Valid @RequestBody ControlRecordRequest request) {
        return controlRecordService.update(agencyId, request);
    }
}
