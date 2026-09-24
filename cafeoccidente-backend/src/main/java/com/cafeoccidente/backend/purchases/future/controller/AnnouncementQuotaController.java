package com.cafeoccidente.backend.purchases.future.controller;

import com.cafeoccidente.backend.purchases.future.dto.AnnouncementQuotaRequest;
import com.cafeoccidente.backend.purchases.future.dto.AnnouncementQuotaResponse;
import com.cafeoccidente.backend.purchases.future.service.AnnouncementQuotaService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Pantalla "Asignar Cupo" (Access "ACTUALIZA CUPOS"). Solo ADMIN. */
@RestController
@RequestMapping("/api/announcement-quotas/{agencyId}")
@PreAuthorize("hasRole('ADMIN')")
public class AnnouncementQuotaController {

    private final AnnouncementQuotaService announcementQuotaService;

    public AnnouncementQuotaController(AnnouncementQuotaService announcementQuotaService) {
        this.announcementQuotaService = announcementQuotaService;
    }

    @GetMapping
    public List<AnnouncementQuotaResponse> list(@PathVariable Long agencyId) {
        return announcementQuotaService.list(agencyId);
    }

    @GetMapping("/{announcementNumber}")
    public AnnouncementQuotaResponse get(@PathVariable Long agencyId, @PathVariable Integer announcementNumber) {
        return announcementQuotaService.get(agencyId, announcementNumber);
    }

    @PutMapping("/{announcementNumber}")
    public AnnouncementQuotaResponse assign(
            @PathVariable Long agencyId,
            @PathVariable Integer announcementNumber,
            @Valid @RequestBody AnnouncementQuotaRequest request) {
        return announcementQuotaService.assign(agencyId, announcementNumber, request);
    }
}
