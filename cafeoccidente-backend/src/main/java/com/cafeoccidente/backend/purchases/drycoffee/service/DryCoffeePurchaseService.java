package com.cafeoccidente.backend.purchases.drycoffee.service;

import com.cafeoccidente.backend.purchases.drycoffee.dto.DryCoffeePurchaseRequest;
import com.cafeoccidente.backend.purchases.drycoffee.dto.DryCoffeePurchaseResponse;

public interface DryCoffeePurchaseService {
    DryCoffeePurchaseResponse create(DryCoffeePurchaseRequest request);

    DryCoffeePurchaseResponse findById(Long id);
}
