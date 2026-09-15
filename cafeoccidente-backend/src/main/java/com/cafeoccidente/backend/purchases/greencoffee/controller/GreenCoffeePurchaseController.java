package com.cafeoccidente.backend.purchases.greencoffee.controller;

import com.cafeoccidente.backend.purchases.greencoffee.dto.AnnouncementInfoResponse;
import com.cafeoccidente.backend.purchases.greencoffee.dto.GreenCoffeePurchaseRequest;
import com.cafeoccidente.backend.purchases.greencoffee.dto.GreenCoffeePurchaseResponse;
import com.cafeoccidente.backend.purchases.greencoffee.dto.NextInvoiceNumberResponse;
import com.cafeoccidente.backend.purchases.greencoffee.service.GreenCoffeePurchaseCalculation;
import com.cafeoccidente.backend.purchases.greencoffee.service.GreenCoffeePurchaseService;
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
@RequestMapping("/api/purchases/green-coffee")
public class GreenCoffeePurchaseController {

    private final GreenCoffeePurchaseService greenCoffeePurchaseService;

    public GreenCoffeePurchaseController(GreenCoffeePurchaseService greenCoffeePurchaseService) {
        this.greenCoffeePurchaseService = greenCoffeePurchaseService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GreenCoffeePurchaseResponse create(@Valid @RequestBody GreenCoffeePurchaseRequest request) {
        return greenCoffeePurchaseService.create(request);
    }

    @GetMapping("/{id}")
    public GreenCoffeePurchaseResponse findById(@PathVariable Long id) {
        return greenCoffeePurchaseService.findById(id);
    }

    @PostMapping("/preview")
    public GreenCoffeePurchaseCalculation preview(@Valid @RequestBody GreenCoffeePurchaseRequest request) {
        return greenCoffeePurchaseService.preview(request);
    }

    @GetMapping("/next-invoice-number")
    public NextInvoiceNumberResponse nextInvoiceNumber() {
        return greenCoffeePurchaseService.nextInvoiceNumber();
    }

    @GetMapping("/announcement-info")
    public AnnouncementInfoResponse announcementInfo() {
        return greenCoffeePurchaseService.announcementInfo();
    }
}
