package com.cafeoccidente.backend.purchases.remittance.controller;

import com.cafeoccidente.backend.common.security.Roles;
import com.cafeoccidente.backend.purchases.remittance.dto.RemittanceFiles;
import com.cafeoccidente.backend.purchases.remittance.dto.RemittancePurchase;
import com.cafeoccidente.backend.purchases.remittance.dto.RemittanceRequest;
import com.cafeoccidente.backend.purchases.remittance.dto.RemittanceResponse;
import com.cafeoccidente.backend.purchases.remittance.service.RemittanceService;
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

/** Genera Remesa (RF-11, RF-12): Administrador y Usuario, sobre la agencia de trabajo. */
@RestController
@RequestMapping("/api/remittances")
@PreAuthorize(Roles.ADMIN_USER)
public class RemittanceController {

    private final RemittanceService service;

    public RemittanceController(RemittanceService service) {
        this.service = service;
    }

    @GetMapping("/specials")
    public List<String> specials(@RequestParam String fundCode) {
        return service.specials(fundCode);
    }

    @GetMapping("/pending")
    public List<RemittancePurchase> pending(@RequestParam String fundCode,
            @RequestParam(required = false) String specialType) {
        return service.pending(fundCode, specialType);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RemittanceResponse create(@Valid @RequestBody RemittanceRequest request) {
        return service.create(request);
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
