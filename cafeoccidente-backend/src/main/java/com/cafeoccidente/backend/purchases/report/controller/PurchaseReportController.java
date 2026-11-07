package com.cafeoccidente.backend.purchases.report.controller;

import com.cafeoccidente.backend.common.security.Roles;
import com.cafeoccidente.backend.purchases.report.dto.FutureBalanceRow;
import com.cafeoccidente.backend.purchases.report.dto.PurchaseReportRow;
import com.cafeoccidente.backend.purchases.report.service.PurchaseReportService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Generar Reporte Anuncio (RF-24; Administrador y Usuario por Q14), Consulta compras por fecha (RF-25) e informes
 * de Compras a Futuro: Anuncios a Futuro (RF-21.6) y Saldo Anuncios (RF-21.7).
 */
@RestController
@RequestMapping("/api/purchases/reports")
@PreAuthorize(Roles.ADMIN_USER)
public class PurchaseReportController {

    private final PurchaseReportService service;

    public PurchaseReportController(PurchaseReportService service) {
        this.service = service;
    }

    @GetMapping("/by-date")
    public List<PurchaseReportRow> byDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.byDate(from, to);
    }

    @GetMapping("/future-announcement")
    public List<PurchaseReportRow> futureAnnouncement(
            @RequestParam int announcement,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.futureAnnouncement(announcement, from, to);
    }

    @GetMapping("/future-balance")
    public List<FutureBalanceRow> futureBalance(@RequestParam int announcement) {
        return service.futureBalance(announcement);
    }
}
