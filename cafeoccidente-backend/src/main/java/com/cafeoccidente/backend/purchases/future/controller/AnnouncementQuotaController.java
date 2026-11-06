package com.cafeoccidente.backend.purchases.future.controller;

import com.cafeoccidente.backend.common.security.CurrentAgencyProvider;
import com.cafeoccidente.backend.common.security.Roles;
import com.cafeoccidente.backend.purchases.future.dto.AnnouncementQuotaRequest;
import com.cafeoccidente.backend.purchases.future.dto.AnnouncementQuotaResponse;
import com.cafeoccidente.backend.purchases.future.dto.QuotaBalanceResponse;
import com.cafeoccidente.backend.purchases.future.dto.QuotaCandidateResponse;
import com.cafeoccidente.backend.purchases.future.service.AnnouncementQuotaService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Pantalla "Asignar Cupo a Anuncios" (RF-21.1, Access "AN FUR HEUTE"). Administrador y Usuario (Q13). */
@RestController
@RequestMapping("/api/announcement-quotas")
@PreAuthorize(Roles.ADMIN_USER)
public class AnnouncementQuotaController {

    private final AnnouncementQuotaService announcementQuotaService;
    private final CurrentAgencyProvider currentAgencyProvider;

    public AnnouncementQuotaController(AnnouncementQuotaService announcementQuotaService, CurrentAgencyProvider currentAgencyProvider) {
        this.announcementQuotaService = announcementQuotaService;
        this.currentAgencyProvider = currentAgencyProvider;
    }

    @GetMapping
    public List<AnnouncementQuotaResponse> list() {
        return announcementQuotaService.list(currentAgencyProvider.currentAgencyId());
    }

    @GetMapping("/available")
    public List<QuotaCandidateResponse> available() {
        return announcementQuotaService.available(currentAgencyProvider.currentAgencyId());
    }

    /** Ventana de Facturar Anuncios con Cupos (RF-21.2). */
    @GetMapping("/with-balance")
    public List<QuotaBalanceResponse> withBalance(@RequestParam Long fundId, @RequestParam String specialType) {
        return announcementQuotaService.withBalance(currentAgencyProvider.currentAgencyId(), fundId, specialType);
    }

    @GetMapping("/{announcementNumber}")
    public AnnouncementQuotaResponse get(@PathVariable Integer announcementNumber) {
        return announcementQuotaService.get(currentAgencyProvider.currentAgencyId(), announcementNumber);
    }

    @PutMapping("/{announcementNumber}")
    public AnnouncementQuotaResponse assign(
            @PathVariable Integer announcementNumber,
            @Valid @RequestBody AnnouncementQuotaRequest request) {
        return announcementQuotaService.assign(currentAgencyProvider.currentAgencyId(), announcementNumber, request);
    }
}
