package com.cafeoccidente.backend.annulmentlog.controller;

import com.cafeoccidente.backend.annulmentlog.dto.AnnulmentLogFilter;
import com.cafeoccidente.backend.annulmentlog.dto.AnnulmentLogPage;
import com.cafeoccidente.backend.annulmentlog.service.AnnulmentLogService;
import com.cafeoccidente.backend.common.security.Roles;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Consultar anulaciones (Acceso Principal): solo el Administrador, todas las agencias. Solo lectura. */
@RestController
@RequestMapping("/api/annulment-log")
@PreAuthorize(Roles.ADMIN)
public class AnnulmentLogController {

    /** Una pagina en pantalla es chica; la exportacion a Excel pide hasta 5000 filas de una vez. */
    private static final int MAX_PAGE_SIZE = 5000;

    private final AnnulmentLogService service;

    public AnnulmentLogController(AnnulmentLogService service) {
        this.service = service;
    }

    @GetMapping
    public AnnulmentLogPage search(
            @RequestParam(required = false) Long agencyId,
            @RequestParam(required = false) String documentType,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "true") boolean includeAccess,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        String type = documentType == null || documentType.isBlank() ? null : documentType;
        return service.search(new AnnulmentLogFilter(agencyId, type, userId, from, to, includeAccess, Math.max(page, 0),
                Math.clamp(size, 1, MAX_PAGE_SIZE)));
    }
}
