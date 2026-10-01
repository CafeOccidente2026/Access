package com.cafeoccidente.backend.vendors.controller;

import com.cafeoccidente.backend.common.security.SecurityUtils;
import com.cafeoccidente.backend.vendors.dto.AssociatePage;
import com.cafeoccidente.backend.vendors.dto.AssociateResponse;
import com.cafeoccidente.backend.vendors.dto.BeneficiaryRow;
import com.cafeoccidente.backend.vendors.dto.NessQuotaBalanceRow;
import com.cafeoccidente.backend.vendors.dto.NessQuotaPage;
import com.cafeoccidente.backend.vendors.dto.VendorCreateRequest;
import com.cafeoccidente.backend.vendors.service.VendorReportService;
import com.cafeoccidente.backend.vendors.service.VendorService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * "MENUS VENDEDORES" (ADMIN y USER). Informes: USER ve solo su agencia; ADMIN la que pida o todas.
 * Asociados y la tabla NESS se consultan completos, como en Access.
 */
@RestController
@RequestMapping("/api/vendors")
public class VendorController {

    private final VendorService vendorService;
    private final VendorReportService vendorReportService;
    private final SecurityUtils securityUtils;

    public VendorController(
            VendorService vendorService, VendorReportService vendorReportService, SecurityUtils securityUtils) {
        this.vendorService = vendorService;
        this.vendorReportService = vendorReportService;
        this.securityUtils = securityUtils;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AssociateResponse create(@Valid @RequestBody VendorCreateRequest request) {
        return vendorService.create(request, securityUtils.resolveAgencyId(request.agencyId()));
    }

    @GetMapping("/associates")
    public AssociatePage associateAt(@RequestParam(defaultValue = "0") long position) {
        return vendorService.associateAt(position);
    }

    @GetMapping("/associates/{idNumber}")
    public AssociatePage findAssociate(@PathVariable String idNumber) {
        return vendorService.findAssociate(idNumber);
    }

    @GetMapping("/reports/beneficiary")
    public List<BeneficiaryRow> beneficiary(
            @RequestParam(required = false) Long agencyId,
            @RequestParam(required = false) String idNumber,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return vendorReportService.beneficiary(agency(agencyId), idNumber, from, to);
    }

    @GetMapping("/ness-quotas")
    public NessQuotaPage nessQuotas(
            @RequestParam(required = false) String idNumber, @RequestParam(defaultValue = "0") int page) {
        return vendorReportService.nessQuotas(idNumber, page);
    }

    @GetMapping("/ness-quota-balances")
    public List<NessQuotaBalanceRow> nessQuotaBalances(@RequestParam(required = false) Long agencyId) {
        return vendorReportService.nessQuotaBalances(agency(agencyId));
    }

    private Long agency(Long requested) {
        return securityUtils.isAdmin() ? requested : securityUtils.getCurrentAgencyId();
    }
}
