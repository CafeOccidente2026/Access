package com.cafeoccidente.backend.purchases.husk.controller;

import com.cafeoccidente.backend.purchases.husk.dto.AnnouncementInfoResponse;
import com.cafeoccidente.backend.purchases.husk.dto.HuskPurchaseRequest;
import com.cafeoccidente.backend.purchases.husk.dto.HuskPurchaseResponse;
import com.cafeoccidente.backend.purchases.husk.dto.NextInvoiceNumberResponse;
import com.cafeoccidente.backend.purchases.husk.service.HuskPurchaseCalculation;
import com.cafeoccidente.backend.purchases.husk.service.HuskPurchaseService;
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
@RequestMapping("/api/purchases/husk")
public class HuskPurchaseController {

    private final HuskPurchaseService huskPurchaseService;

    public HuskPurchaseController(HuskPurchaseService huskPurchaseService) {
        this.huskPurchaseService = huskPurchaseService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public HuskPurchaseResponse create(@Valid @RequestBody HuskPurchaseRequest request) {
        return huskPurchaseService.create(request);
    }

    @GetMapping("/{id}")
    public HuskPurchaseResponse findById(@PathVariable Long id) {
        return huskPurchaseService.findById(id);
    }

    @PostMapping("/preview")
    public HuskPurchaseCalculation preview(@Valid @RequestBody HuskPurchaseRequest request) {
        return huskPurchaseService.preview(request);
    }

    @GetMapping("/next-invoice-number")
    public NextInvoiceNumberResponse nextInvoiceNumber() {
        return huskPurchaseService.nextInvoiceNumber();
    }

    @GetMapping("/announcement-info")
    public AnnouncementInfoResponse announcementInfo() {
        return huskPurchaseService.announcementInfo();
    }
}
