package com.cafeoccidente.backend.inventory.controller;

import com.cafeoccidente.backend.common.security.SecurityUtils;
import com.cafeoccidente.backend.inventory.dto.InventoryReportRow;
import com.cafeoccidente.backend.inventory.dto.ProductCodeOption;
import com.cafeoccidente.backend.inventory.service.InventoryReportService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory-report")
public class InventoryReportController {

    private final InventoryReportService inventoryReportService;
    private final SecurityUtils securityUtils;

    public InventoryReportController(InventoryReportService inventoryReportService, SecurityUtils securityUtils) {
        this.inventoryReportService = inventoryReportService;
        this.securityUtils = securityUtils;
    }

    /** USER: siempre su agencia de sesion, ignora el agencyId pedido (SecurityUtils.resolveAgencyId). */
    @GetMapping
    public List<InventoryReportRow> report(
            @RequestParam Long agencyId,
            @RequestParam(required = false) String productCode,
            @RequestParam(required = false) String specialType) {
        return inventoryReportService.report(
                securityUtils.resolveAgencyId(agencyId), productCode, specialType, LocalDate.now());
    }

    @GetMapping("/product-codes")
    public List<ProductCodeOption> productCodes() {
        return inventoryReportService.productCodes();
    }
}
