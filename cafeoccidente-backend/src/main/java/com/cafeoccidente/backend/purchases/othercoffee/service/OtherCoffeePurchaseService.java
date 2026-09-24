package com.cafeoccidente.backend.purchases.othercoffee.service;

import com.cafeoccidente.backend.purchases.othercoffee.dto.NextInvoiceNumberResponse;
import com.cafeoccidente.backend.purchases.othercoffee.dto.OtherCoffeePurchaseRequest;
import com.cafeoccidente.backend.purchases.othercoffee.dto.OtherCoffeePurchaseResponse;
import com.cafeoccidente.backend.purchases.othercoffee.dto.QualityPercentagesResponse;
import com.cafeoccidente.backend.purchases.othercoffee.dto.SpecialInfoResponse;
import java.math.BigDecimal;

public interface OtherCoffeePurchaseService {
    OtherCoffeePurchaseResponse create(OtherCoffeePurchaseRequest request);

    OtherCoffeePurchaseResponse findById(Long id);

    OtherCoffeePurchaseCalculation preview(OtherCoffeePurchaseRequest request);

    NextInvoiceNumberResponse nextInvoiceNumber();

    SpecialInfoResponse specialInfo(Long agencyId, Long fundId, String specialType);

    QualityPercentagesResponse qualityPercentages(
            BigDecimal totalStoredWeight, BigDecimal defectiveStoredWeight, BigDecimal healthyStoredWeight);
}
