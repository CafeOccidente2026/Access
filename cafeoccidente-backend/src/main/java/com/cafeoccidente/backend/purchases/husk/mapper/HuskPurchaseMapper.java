package com.cafeoccidente.backend.purchases.husk.mapper;

import com.cafeoccidente.backend.controlrecord.entity.ControlRecord;
import com.cafeoccidente.backend.purchases.husk.dto.HuskPurchaseResponse;
import com.cafeoccidente.backend.purchases.husk.entity.HuskPurchase;
import org.springframework.stereotype.Component;

@Component
public class HuskPurchaseMapper {

    /**
     * @param controlRecord ControlRecord VIVO de la agencia de la compra (igual que Cafe Seco): trae
     *     los datos de pie de factura (resolucion DIAN, prefijo, punto de compra) del PDF.
     */
    public HuskPurchaseResponse toResponse(HuskPurchase purchase, ControlRecord controlRecord) {
        return new HuskPurchaseResponse(
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
                purchase.getBasePriceDryLoad(),
                purchase.getIdNumber(),
                purchase.getFirstName(),
                purchase.getLastName(),
                purchase.getGrowerType(),
                purchase.getAddress(),
                purchase.getCellphone(),
                purchase.getPointPrice(),
                purchase.getCosts(),
                purchase.getAlmondWeight(),
                purchase.getAlmondPercentage(),
                purchase.getBagsCount(),
                purchase.getGrossKg(),
                purchase.getTareKg(),
                purchase.getNetKg(),
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
