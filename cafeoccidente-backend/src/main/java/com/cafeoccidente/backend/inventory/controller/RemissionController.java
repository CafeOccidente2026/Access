package com.cafeoccidente.backend.inventory.controller;

import com.cafeoccidente.backend.common.security.CurrentAgencyProvider;
import com.cafeoccidente.backend.common.security.Roles;
import com.cafeoccidente.backend.inventory.dto.RemissionRequest;
import com.cafeoccidente.backend.inventory.dto.RemissionResponse;
import com.cafeoccidente.backend.inventory.service.RemissionService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/remissions")
@PreAuthorize(Roles.ADMIN_USER)
public class RemissionController {

    private final RemissionService remissionService;
    private final CurrentAgencyProvider currentAgencyProvider;

    public RemissionController(RemissionService remissionService, CurrentAgencyProvider currentAgencyProvider) {
        this.remissionService = remissionService;
        this.currentAgencyProvider = currentAgencyProvider;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RemissionResponse create(@Valid @RequestBody RemissionRequest request) {
        return remissionService.create(request);
    }

    @GetMapping("/{id}")
    public RemissionResponse findById(@PathVariable Long id) {
        return remissionService.findById(id);
    }

    /** "Reimprimir Remision" busca por numero+agencia; sin numero, lista todas (mas nueva primero).
     *  Siempre la agencia de trabajo (RF-05.1). */
    @GetMapping
    public List<RemissionResponse> list(
            @RequestParam(required = false) String remissionNumber,
            @RequestParam(required = false, defaultValue = "false") boolean pendingExport) {
        Long agencyId = currentAgencyProvider.currentAgencyId();
        if (remissionNumber != null && !remissionNumber.isBlank()) {
            return remissionService.findByNumber(agencyId, remissionNumber).map(List::of).orElseGet(List::of);
        }
        return pendingExport ? remissionService.listPendingExport(agencyId) : remissionService.listByAgency(agencyId);
    }

    /** "Genera Remesa"/"Genera Remesa Otros" (Form_Genera Plano.bas): marca como exportadas tras
     *  descargar el Excel en el navegador (reemplaza el TransferSpreadsheet a D:\ + envio a SAP). */
    @PostMapping("/mark-exported")
    public List<RemissionResponse> markExported(@RequestBody List<Long> remissionIds) {
        return remissionService.markExported(remissionIds);
    }
}
