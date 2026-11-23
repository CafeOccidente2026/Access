package com.cafeoccidente.backend.purchases.remittance.controller;

import com.cafeoccidente.backend.common.security.Roles;
import com.cafeoccidente.backend.purchases.remittance.dto.RemittanceFiles;
import com.cafeoccidente.backend.purchases.remittance.dto.RemittanceResponse;
import com.cafeoccidente.backend.purchases.remittance.service.RemittanceService;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Remesas (RF-11, RF-12): buscar, sus 4 archivos y Desmarca Remesa; las genera la remision (Registrar Salidas). */
@RestController
@RequestMapping("/api/remittances")
@PreAuthorize(Roles.ADMIN_USER)
public class RemittanceController {

    private final RemittanceService service;

    public RemittanceController(RemittanceService service) {
        this.service = service;
    }

    @GetMapping
    public List<RemittanceResponse> findByNumber(@RequestParam String number) {
        return service.findByNumber(number);
    }

    @GetMapping("/{id}/files")
    public RemittanceFiles files(@PathVariable Long id) {
        return service.files(id);
    }

    /** "Desmarca Remesa": solo el Administrador (decision del usuario, 2026-10-30). */
    @PostMapping("/{id}/unmark")
    @PreAuthorize(Roles.ADMIN)
    public RemittanceResponse unmark(@PathVariable Long id) {
        return service.unmark(id);
    }
}
