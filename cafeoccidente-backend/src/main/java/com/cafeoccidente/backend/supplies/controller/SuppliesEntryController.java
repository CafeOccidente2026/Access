package com.cafeoccidente.backend.supplies.controller;

import com.cafeoccidente.backend.supplies.dto.EntryResponse;
import com.cafeoccidente.backend.supplies.dto.LedgerEntryRequest;
import com.cafeoccidente.backend.supplies.dto.PackagingEntryRequest;
import com.cafeoccidente.backend.supplies.service.SuppliesEntryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Altas de "MENUS SUMINISTROS" (ADMIN y USER; la agencia la resuelve el servicio por rol). */
@RestController
@RequestMapping("/api/supplies")
@ResponseStatus(HttpStatus.CREATED)
public class SuppliesEntryController {

    private final SuppliesEntryService suppliesEntryService;

    public SuppliesEntryController(SuppliesEntryService suppliesEntryService) {
        this.suppliesEntryService = suppliesEntryService;
    }

    @PostMapping("/cash")
    public EntryResponse createCashSupply(@Valid @RequestBody LedgerEntryRequest request) {
        return suppliesEntryService.createCashSupply(request);
    }

    @PostMapping("/cash-adjustments")
    public EntryResponse createCashAdjustment(@Valid @RequestBody LedgerEntryRequest request) {
        return suppliesEntryService.createCashAdjustment(request);
    }

    @PostMapping("/petty-cash")
    public EntryResponse createPettyCashSupply(@Valid @RequestBody LedgerEntryRequest request) {
        return suppliesEntryService.createPettyCashSupply(request);
    }

    @PostMapping("/petty-cash-expenses")
    public EntryResponse createPettyCashExpense(@Valid @RequestBody LedgerEntryRequest request) {
        return suppliesEntryService.createPettyCashExpense(request);
    }

    @PostMapping("/supplies")
    public EntryResponse createSupply(@Valid @RequestBody LedgerEntryRequest request) {
        return suppliesEntryService.createSupply(request);
    }

    @PostMapping("/issued-checks")
    public EntryResponse createIssuedCheck(@Valid @RequestBody LedgerEntryRequest request) {
        return suppliesEntryService.createIssuedCheck(request);
    }

    @PostMapping("/packaging")
    public EntryResponse createPackagingEntry(@Valid @RequestBody PackagingEntryRequest request) {
        return suppliesEntryService.createPackagingEntry(request);
    }

    @PostMapping("/packaging-loans")
    public EntryResponse createPackagingLoan(@Valid @RequestBody PackagingEntryRequest request) {
        return suppliesEntryService.createPackagingLoan(request);
    }
}
