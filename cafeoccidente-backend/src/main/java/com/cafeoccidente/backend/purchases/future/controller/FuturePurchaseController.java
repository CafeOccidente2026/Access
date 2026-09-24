package com.cafeoccidente.backend.purchases.future.controller;

import com.cafeoccidente.backend.purchases.future.dto.FuturePurchaseRequest;
import com.cafeoccidente.backend.purchases.future.dto.FuturePurchaseResponse;
import com.cafeoccidente.backend.purchases.future.service.FuturePurchaseService;
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
@RequestMapping("/api/purchases/future")
public class FuturePurchaseController {

    private final FuturePurchaseService futurePurchaseService;

    public FuturePurchaseController(FuturePurchaseService futurePurchaseService) {
        this.futurePurchaseService = futurePurchaseService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FuturePurchaseResponse create(@Valid @RequestBody FuturePurchaseRequest request) {
        return futurePurchaseService.create(request);
    }

    @GetMapping("/{id}")
    public FuturePurchaseResponse findById(@PathVariable Long id) {
        return futurePurchaseService.findById(id);
    }
}
