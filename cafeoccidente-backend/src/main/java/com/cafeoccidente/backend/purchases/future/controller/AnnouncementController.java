package com.cafeoccidente.backend.purchases.future.controller;

import com.cafeoccidente.backend.purchases.future.dto.AnnouncementResponse;
import com.cafeoccidente.backend.purchases.future.service.AnnouncementService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Solo lectura: no hay administracion de anuncios todavia (pantalla ADMIN queda pendiente). */
@RestController
@RequestMapping("/api/announcements")
public class AnnouncementController {

    private final AnnouncementService announcementService;

    public AnnouncementController(AnnouncementService announcementService) {
        this.announcementService = announcementService;
    }

    @GetMapping("/latest")
    public AnnouncementResponse latest(@RequestParam Long agencyId, @RequestParam Long fundId) {
        return announcementService.findLatest(agencyId, fundId);
    }
}
