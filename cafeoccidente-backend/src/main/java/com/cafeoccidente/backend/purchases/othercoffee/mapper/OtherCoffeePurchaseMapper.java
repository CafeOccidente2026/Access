package com.cafeoccidente.backend.purchases.othercoffee.mapper;

import com.cafeoccidente.backend.controlrecord.entity.ControlRecord;
import com.cafeoccidente.backend.purchases.othercoffee.dto.OtherCoffeePurchaseResponse;
import com.cafeoccidente.backend.purchases.othercoffee.entity.OtherCoffeePurchase;
import org.springframework.stereotype.Component;

@Component
public class OtherCoffeePurchaseMapper {

    public OtherCoffeePurchaseResponse toResponse(OtherCoffeePurchase purchase, ControlRecord controlRecord) {
        return new OtherCoffeePurchaseResponse(
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
                purchase.getNetKg(),
                purchase.getTotalStoredWeight(),
                purchase.getWastePercentage(),
                purchase.getDefectiveStoredWeight(),
                purchase.getDefectivePercentage(),
                purchase.getHealthyStoredWeight(),
                purchase.getHealthyPercentage(),
                purchase.getHealthyUnitPrice(),
                purchase.getDefectiveUnitPrice(),
                purchase.getBonus(),
                purchase.getPenalty(),
                purchase.getCosts(),
                purchase.getUnitPrice(),
                purchase.getGrossValue(),
                purchase.getInventoryValue(),
                purchase.getAssociateContribution(),
                purchase.getCooperativeDiscount(),
                purchase.isWithholdingExempt(),
                purchase.getWithholding(),
                purchase.getFreightDiscount(),
                purchase.getOtherDiscounts(),
                purchase.getNetToPay(),
                purchase.getPaymentMethod(),
                purchase.getCheckNumber(),
                purchase.getCreatedByUserId(),
                controlRecord.getPurchasePoint(),
                controlRecord.getPrefix(),
                controlRecord.getDianResolution(),
                controlRecord.getResolutionDate(),
                controlRecord.getResolutionFrom(),
                controlRecord.getResolutionTo(),
                controlRecord.getValidity());
    }
}
