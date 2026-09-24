package com.cafeoccidente.backend.purchases.future.service;

import com.cafeoccidente.backend.purchases.future.dto.FertiFuturoPurchaseRequest;
import com.cafeoccidente.backend.purchases.future.dto.FertiFuturoPurchaseResponse;
import com.cafeoccidente.backend.purchases.future.dto.NextInvoiceNumberResponse;

public interface FertiFuturoPurchaseService {

    FertiFuturoPurchaseResponse create(FertiFuturoPurchaseRequest request);

    FertiFuturoPurchaseResponse findById(Long id);

    /** Recalcula la cascada completa sin persistir - mismo patron que los otros 4 modulos de compra. */
    FertiFuturoPurchaseCalculation preview(FertiFuturoPurchaseRequest request);

    NextInvoiceNumberResponse nextInvoiceNumber();
}
