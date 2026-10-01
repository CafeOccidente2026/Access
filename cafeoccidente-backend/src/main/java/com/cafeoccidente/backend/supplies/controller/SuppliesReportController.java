package com.cafeoccidente.backend.supplies.controller;

import com.cafeoccidente.backend.common.security.SecurityUtils;
import com.cafeoccidente.backend.supplies.dto.SuppliesReportRow;
import com.cafeoccidente.backend.supplies.service.SuppliesReportService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Informes de "MENUS SUMINISTROS". USER ve solo su agencia; ADMIN la que pida o todas (sin agencyId).
 * Relacion Cheques es POST porque, como en Access, deja marcados los cheques que muestra.
 */
@RestController
@RequestMapping("/api/supplies/reports")
public class SuppliesReportController {

    private final SuppliesReportService suppliesReportService;
    private final SecurityUtils securityUtils;

    public SuppliesReportController(SuppliesReportService suppliesReportService, SecurityUtils securityUtils) {
        this.suppliesReportService = suppliesReportService;
        this.securityUtils = securityUtils;
    }

    @GetMapping("/cash")
    public List<SuppliesReportRow> cashMovement(
            @RequestParam(required = false) Long agencyId, @RequestParam(required = false) String fund) {
        return suppliesReportService.cashMovement(agency(agencyId), fund, LocalDate.now());
    }

    @GetMapping("/petty-cash")
    public List<SuppliesReportRow> pettyCashMovement(@RequestParam(required = false) Long agencyId) {
        return suppliesReportService.pettyCashMovement(agency(agencyId), LocalDate.now());
    }

    @GetMapping("/supplies")
    public List<SuppliesReportRow> supplies(@RequestParam(required = false) Long agencyId, @RequestParam String fund) {
        return suppliesReportService.supplies(agency(agencyId), fund, LocalDate.now());
    }

    @GetMapping("/packaging")
    public List<SuppliesReportRow> packaging(
            @RequestParam(required = false) Long agencyId, @RequestParam(required = false) String type) {
        return suppliesReportService.packaging(agency(agencyId), type, LocalDate.now());
    }

    @PostMapping("/checks")
    public List<SuppliesReportRow> checkRelation(
            @RequestParam(required = false) Long agencyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return suppliesReportService.checkRelation(agency(agencyId), from, to, LocalDate.now());
    }

    @GetMapping("/payment-methods")
    public List<SuppliesReportRow> paymentMethods(
            @RequestParam(required = false) Long agencyId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String method) {
        return suppliesReportService.paymentMethods(agency(agencyId), from, to, method);
    }

    private Long agency(Long requested) {
        return securityUtils.isAdmin() ? requested : securityUtils.getCurrentAgencyId();
    }
}
