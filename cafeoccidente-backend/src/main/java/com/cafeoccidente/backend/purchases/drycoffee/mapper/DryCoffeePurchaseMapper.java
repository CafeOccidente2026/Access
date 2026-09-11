package com.cafeoccidente.backend.purchases.drycoffee.mapper;

import com.cafeoccidente.backend.purchases.drycoffee.dto.DryCoffeePurchaseResponse;
import com.cafeoccidente.backend.purchases.drycoffee.entity.DryCoffeePurchase;
import org.springframework.stereotype.Component;

/** Unica responsabilidad: traducir la entidad DryCoffeePurchase a su DTO de respuesta. */
@Component
public class DryCoffeePurchaseMapper {

    public DryCoffeePurchaseResponse toResponse(DryCoffeePurchase purchase) {
        return new DryCoffeePurchaseResponse(
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
                purchase.getCreatedByUserId());
    }
}
