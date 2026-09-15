package com.cafeoccidente.backend.purchases.greencoffee.mapper;

import com.cafeoccidente.backend.purchases.greencoffee.dto.GreenCoffeePurchaseResponse;
import com.cafeoccidente.backend.purchases.greencoffee.entity.GreenCoffeePurchase;
import org.springframework.stereotype.Component;

@Component
public class GreenCoffeePurchaseMapper {

    public GreenCoffeePurchaseResponse toResponse(GreenCoffeePurchase purchase) {
        return new GreenCoffeePurchaseResponse(
                purchase.getId(),
                purchase.getPurchaseDate(),
                purchase.getInvoiceNumber(),
                purchase.getAgency().getId(),
                purchase.getAgency().getName(),
                purchase.getFund().getId(),
                purchase.getFund().getCode(),
                purchase.getSpecialType(),
                purchase.getProductCode().getCode(),
                purchase.getAnnouncementNumber(),
                purchase.getAnnouncementDate(),
                purchase.getBasePriceLoad(),
                purchase.getIdNumber(),
                purchase.getFirstName(),
                purchase.getLastName(),
                purchase.getGrowerType(),
                purchase.getAddress(),
                purchase.getCellphone(),
                purchase.getBagsCount(),
                purchase.getGrossKg(),
                purchase.getTareKg(),
                purchase.getGreenKg(),
                purchase.getNetKg(),
                purchase.getHealthyUnitPrice(),
                purchase.getDefectiveUnitPrice(),
                purchase.getBonus(),
                purchase.getCosts(),
                purchase.getPenalty(),
                purchase.getCompKgPrice(),
                purchase.getUnitPrice(),
                purchase.getGrossValue(),
                purchase.getInventoryValue(),
                purchase.getAssociateContribution(),
                purchase.getCooperativeDiscount(),
                purchase.isWithholdingExempt(),
                purchase.getWithholding(),
                purchase.getShrinkageDiscount(),
                purchase.getOtherDiscounts(),
                purchase.getNetToPay(),
                purchase.getPaymentMethod(),
                purchase.getCheckNumber(),
                purchase.getCreatedByUserId());
    }
}
