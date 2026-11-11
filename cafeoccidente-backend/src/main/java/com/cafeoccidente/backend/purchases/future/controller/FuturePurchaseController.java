package com.cafeoccidente.backend.purchases.future.controller;

import com.cafeoccidente.backend.common.security.Roles;
import com.cafeoccidente.backend.purchases.future.dto.AnnouncementResponse;
import com.cafeoccidente.backend.purchases.future.dto.FuturePurchaseRequest;
import com.cafeoccidente.backend.purchases.future.dto.FuturePurchaseResponse;
import com.cafeoccidente.backend.purchases.future.dto.FuturePurchaseRestoreRow;
import com.cafeoccidente.backend.purchases.future.dto.PendingAnnouncementRow;
import com.cafeoccidente.backend.purchases.future.service.FuturePurchaseRestoreService;
import com.cafeoccidente.backend.purchases.future.service.FuturePurchaseService;
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
@RequestMapping("/api/purchases/future")
@PreAuthorize(Roles.ADMIN_USER)
public class FuturePurchaseController {

    private final FuturePurchaseService futurePurchaseService;
    private final FuturePurchaseRestoreService restoreService;

    public FuturePurchaseController(
            FuturePurchaseService futurePurchaseService, FuturePurchaseRestoreService restoreService) {
        this.futurePurchaseService = futurePurchaseService;
        this.restoreService = restoreService;
    }

    /** Restaurar Compras Anunciadas (Q5: solo el Administrador): lo que quedaria, sin escribir. */
    @GetMapping("/restore")
    @PreAuthorize(Roles.ADMIN)
    public List<FuturePurchaseRestoreRow> restorePreview(@RequestParam String idNumber) {
        return restoreService.preview(idNumber);
    }

    /** Recalcula el saldo desde cero (P6) y lo deja en el Historial. */
    @PostMapping("/restore")
    @PreAuthorize(Roles.ADMIN)
    public List<FuturePurchaseRestoreRow> restore(@RequestParam String idNumber) {
        return restoreService.restore(idNumber);
    }

    /** Anunciadas de Hoy (HU-31, P2): solo lectura. */
    @GetMapping("/pending")
    public List<PendingAnnouncementRow> pending() {
        return futurePurchaseService.pending();
    }

    /** Ventana de Facturar Compras Anunciadas (RN-21.5.2). */
    @GetMapping("/open")
    public List<FuturePurchaseResponse> openFor(@RequestParam String idNumber) {
        return futurePurchaseService.openFor(idNumber);
    }

    /** Lista de anuncios del dia del especial (RN-21.4.3). */
    @GetMapping("/announcements")
    public List<AnnouncementResponse> todayAnnouncements(@RequestParam String specialType) {
        return futurePurchaseService.todayAnnouncements(specialType);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FuturePurchaseResponse create(@Valid @RequestBody FuturePurchaseRequest request) {
        return futurePurchaseService.create(request);
    }

    @GetMapping("/{id}")
    public FuturePurchaseResponse findById(@PathVariable Long id) {
        return futurePurchaseService.findById(id);
    }
}
