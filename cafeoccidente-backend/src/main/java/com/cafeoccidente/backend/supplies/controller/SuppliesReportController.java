package com.cafeoccidente.backend.supplies.controller;

import com.cafeoccidente.backend.common.security.CurrentAgencyProvider;
import com.cafeoccidente.backend.common.security.Roles;
import com.cafeoccidente.backend.supplies.dto.SuppliesReportRow;
import com.cafeoccidente.backend.supplies.service.SuppliesReportService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Informes de "MENUS SUMINISTROS". Siempre la agencia de trabajo (RN-05.1.2).
 * Relacion Cheques es POST porque, como en Access, deja marcados los cheques que muestra.
 */
@RestController
@RequestMapping("/api/supplies/reports")
@PreAuthorize(Roles.ADMIN_USER)
public class SuppliesReportController {

    private final SuppliesReportService suppliesReportService;
    private final CurrentAgencyProvider currentAgencyProvider;

    public SuppliesReportController(SuppliesReportService suppliesReportService, CurrentAgencyProvider currentAgencyProvider) {
        this.suppliesReportService = suppliesReportService;
        this.currentAgencyProvider = currentAgencyProvider;
    }

    @GetMapping("/cash")
    public List<SuppliesReportRow> cashMovement(
            @RequestParam(required = false) String fund) {
        return suppliesReportService.cashMovement(agency(), fund, LocalDate.now());
    }

    @GetMapping("/petty-cash")
    public List<SuppliesReportRow> pettyCashMovement() {
        return suppliesReportService.pettyCashMovement(agency(), LocalDate.now());
    }

    @GetMapping("/supplies")
    public List<SuppliesReportRow> supplies(@RequestParam String fund) {
        return suppliesReportService.supplies(agency(), fund, LocalDate.now());
    }

    @GetMapping("/packaging")
    public List<SuppliesReportRow> packaging(
            @RequestParam(required = false) String type) {
        return suppliesReportService.packaging(agency(), type, LocalDate.now());
    }

    @PostMapping("/checks")
    public List<SuppliesReportRow> checkRelation(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return suppliesReportService.checkRelation(agency(), from, to, LocalDate.now());
    }

    @GetMapping("/payment-methods")
    public List<SuppliesReportRow> paymentMethods(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String method) {
        return suppliesReportService.paymentMethods(agency(), from, to, method);
    }

    /** Siempre la agencia de trabajo (RN-05.1.2), tambien Relacion Cheques. */
    private Long agency() {
        return currentAgencyProvider.currentAgencyId();
    }
}
