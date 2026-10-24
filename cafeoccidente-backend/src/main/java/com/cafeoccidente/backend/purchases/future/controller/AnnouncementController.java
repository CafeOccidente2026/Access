package com.cafeoccidente.backend.purchases.future.controller;

import com.cafeoccidente.backend.common.security.CurrentAgencyProvider;
import com.cafeoccidente.backend.common.security.Roles;
import com.cafeoccidente.backend.purchases.future.dto.AnnouncementRequest;
import com.cafeoccidente.backend.purchases.future.dto.AnnouncementResponse;
import com.cafeoccidente.backend.purchases.future.dto.HuskAnnouncementRequest;
import com.cafeoccidente.backend.purchases.future.service.AnnouncementService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/announcements")
@PreAuthorize(Roles.ADMIN_USER)
public class AnnouncementController {

    private final AnnouncementService announcementService;
    private final CurrentAgencyProvider currentAgencyProvider;

    public AnnouncementController(AnnouncementService announcementService, CurrentAgencyProvider currentAgencyProvider) {
        this.announcementService = announcementService;
        this.currentAgencyProvider = currentAgencyProvider;
    }

    @GetMapping("/latest")
    public AnnouncementResponse latest(
            @RequestParam Long fundId, @RequestParam String specialType) {
        return announcementService.findLatest(currentAgencyProvider.currentAgencyId(), fundId, specialType);
    }

    /** "Actualizar Anuncio con Factor" (Administrador y Operaciones, RF-08): siempre crea un anuncio nuevo. */
    @PostMapping
    @PreAuthorize(Roles.ANNOUNCEMENTS)
    @ResponseStatus(HttpStatus.CREATED)
    public AnnouncementResponse create(@Valid @RequestBody AnnouncementRequest request) {
        return announcementService.create(request);
    }

    /** "Actualizar Anuncio Pasilla" (RF-09): Fondo RP y Especial PASILLA van fijos. */
    @PostMapping("/husk")
    @PreAuthorize(Roles.ANNOUNCEMENTS)
    @ResponseStatus(HttpStatus.CREATED)
    public AnnouncementResponse createHusk(@Valid @RequestBody HuskAnnouncementRequest request) {
        return announcementService.createHusk(request);
    }

    @GetMapping("/history")
    @PreAuthorize(Roles.ANNOUNCEMENTS)
    public List<AnnouncementResponse> history() {
        return announcementService.history();
    }
}
