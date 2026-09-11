package com.cafeoccidente.backend.purchases.drycoffee.controller;

import com.cafeoccidente.backend.purchases.drycoffee.dto.DryCoffeePurchaseRequest;
import com.cafeoccidente.backend.purchases.drycoffee.dto.DryCoffeePurchaseResponse;
import com.cafeoccidente.backend.purchases.drycoffee.dto.NextInvoiceNumberResponse;
import com.cafeoccidente.backend.purchases.drycoffee.dto.QualityPercentagesResponse;
import com.cafeoccidente.backend.purchases.drycoffee.dto.SpecialInfoResponse;
import com.cafeoccidente.backend.purchases.drycoffee.service.DryCoffeePurchaseCalculation;
import com.cafeoccidente.backend.purchases.drycoffee.service.DryCoffeePurchaseService;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/purchases/dry-coffee")
public class DryCoffeePurchaseController {

    private final DryCoffeePurchaseService dryCoffeePurchaseService;

    public DryCoffeePurchaseController(DryCoffeePurchaseService dryCoffeePurchaseService) {
        this.dryCoffeePurchaseService = dryCoffeePurchaseService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DryCoffeePurchaseResponse create(@Valid @RequestBody DryCoffeePurchaseRequest request) {
        return dryCoffeePurchaseService.create(request);
    }

    @GetMapping("/{id}")
    public DryCoffeePurchaseResponse findById(@PathVariable Long id) {
        return dryCoffeePurchaseService.findById(id);
    }

    @PostMapping("/preview")
    public DryCoffeePurchaseCalculation preview(@Valid @RequestBody DryCoffeePurchaseRequest request) {
        return dryCoffeePurchaseService.preview(request);
    }

    @GetMapping("/next-invoice-number")
    public NextInvoiceNumberResponse nextInvoiceNumber() {
        return dryCoffeePurchaseService.nextInvoiceNumber();
    }

    @GetMapping("/special-info")
    public SpecialInfoResponse specialInfo(
            @RequestParam Long agencyId, @RequestParam Long fundId, @RequestParam String specialType) {
        return dryCoffeePurchaseService.specialInfo(agencyId, fundId, specialType);
    }

    @GetMapping("/quality-percentages")
    public QualityPercentagesResponse qualityPercentages(
            @RequestParam(required = false) BigDecimal totalStoredWeight,
            @RequestParam(required = false) BigDecimal defectiveStoredWeight,
            @RequestParam(required = false) BigDecimal healthyStoredWeight) {
        return dryCoffeePurchaseService.qualityPercentages(
                totalStoredWeight, defectiveStoredWeight, healthyStoredWeight);
    }
}
