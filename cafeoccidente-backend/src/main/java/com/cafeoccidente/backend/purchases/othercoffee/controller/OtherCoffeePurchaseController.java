package com.cafeoccidente.backend.purchases.othercoffee.controller;

import com.cafeoccidente.backend.purchases.othercoffee.dto.NextInvoiceNumberResponse;
import com.cafeoccidente.backend.purchases.othercoffee.dto.OtherCoffeePurchaseRequest;
import com.cafeoccidente.backend.purchases.othercoffee.dto.OtherCoffeePurchaseResponse;
import com.cafeoccidente.backend.purchases.othercoffee.dto.QualityPercentagesResponse;
import com.cafeoccidente.backend.purchases.othercoffee.dto.SpecialInfoResponse;
import com.cafeoccidente.backend.purchases.othercoffee.service.OtherCoffeePurchaseCalculation;
import com.cafeoccidente.backend.purchases.othercoffee.service.OtherCoffeePurchaseService;
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
@RequestMapping("/api/purchases/other-coffee")
public class OtherCoffeePurchaseController {

    private final OtherCoffeePurchaseService otherCoffeePurchaseService;

    public OtherCoffeePurchaseController(OtherCoffeePurchaseService otherCoffeePurchaseService) {
        this.otherCoffeePurchaseService = otherCoffeePurchaseService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OtherCoffeePurchaseResponse create(@Valid @RequestBody OtherCoffeePurchaseRequest request) {
        return otherCoffeePurchaseService.create(request);
    }

    @GetMapping("/{id}")
    public OtherCoffeePurchaseResponse findById(@PathVariable Long id) {
        return otherCoffeePurchaseService.findById(id);
    }

    @PostMapping("/preview")
    public OtherCoffeePurchaseCalculation preview(@Valid @RequestBody OtherCoffeePurchaseRequest request) {
        return otherCoffeePurchaseService.preview(request);
    }

    @GetMapping("/next-invoice-number")
    public NextInvoiceNumberResponse nextInvoiceNumber() {
        return otherCoffeePurchaseService.nextInvoiceNumber();
    }

    @GetMapping("/special-info")
    public SpecialInfoResponse specialInfo(
            @RequestParam Long agencyId, @RequestParam Long fundId, @RequestParam String specialType) {
        return otherCoffeePurchaseService.specialInfo(agencyId, fundId, specialType);
    }

    @GetMapping("/quality-percentages")
    public QualityPercentagesResponse qualityPercentages(
            @RequestParam(required = false) BigDecimal totalStoredWeight,
            @RequestParam(required = false) BigDecimal defectiveStoredWeight,
            @RequestParam(required = false) BigDecimal healthyStoredWeight) {
        return otherCoffeePurchaseService.qualityPercentages(
                totalStoredWeight, defectiveStoredWeight, healthyStoredWeight);
    }
}
