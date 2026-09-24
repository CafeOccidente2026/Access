package com.cafeoccidente.backend.purchases.future.controller;

import com.cafeoccidente.backend.purchases.future.dto.FertiFuturoPurchaseRequest;
import com.cafeoccidente.backend.purchases.future.dto.FertiFuturoPurchaseResponse;
import com.cafeoccidente.backend.purchases.future.dto.NextInvoiceNumberResponse;
import com.cafeoccidente.backend.purchases.future.service.FertiFuturoPurchaseCalculation;
import com.cafeoccidente.backend.purchases.future.service.FertiFuturoPurchaseService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/purchases/ferti-futuro")
public class FertiFuturoPurchaseController {

    private final FertiFuturoPurchaseService fertiFuturoPurchaseService;

    public FertiFuturoPurchaseController(FertiFuturoPurchaseService fertiFuturoPurchaseService) {
        this.fertiFuturoPurchaseService = fertiFuturoPurchaseService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FertiFuturoPurchaseResponse create(@Valid @RequestBody FertiFuturoPurchaseRequest request) {
        return fertiFuturoPurchaseService.create(request);
    }

    @GetMapping("/{id}")
    public FertiFuturoPurchaseResponse findById(@PathVariable Long id) {
        return fertiFuturoPurchaseService.findById(id);
    }

    @PostMapping("/preview")
    public FertiFuturoPurchaseCalculation preview(@Valid @RequestBody FertiFuturoPurchaseRequest request) {
        return fertiFuturoPurchaseService.preview(request);
    }

    @GetMapping("/next-invoice-number")
    public NextInvoiceNumberResponse nextInvoiceNumber() {
        return fertiFuturoPurchaseService.nextInvoiceNumber();
    }
}
