package com.cafeoccidente.backend.purchases.future.service;

import com.cafeoccidente.backend.purchases.future.dto.FuturePurchaseRequest;
import com.cafeoccidente.backend.purchases.future.dto.FuturePurchaseResponse;

public interface FuturePurchaseService {

    FuturePurchaseResponse create(FuturePurchaseRequest request);

    FuturePurchaseResponse findById(Long id);
}
