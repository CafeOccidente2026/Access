package com.cafeoccidente.backend.purchases.drycoffee.controller;

import com.cafeoccidente.backend.purchases.drycoffee.dto.DryCoffeePurchaseRequest;
import com.cafeoccidente.backend.purchases.drycoffee.dto.DryCoffeePurchaseResponse;
import com.cafeoccidente.backend.purchases.drycoffee.service.DryCoffeePurchaseService;
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
}
