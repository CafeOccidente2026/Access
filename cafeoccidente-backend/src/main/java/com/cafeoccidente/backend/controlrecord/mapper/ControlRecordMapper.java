package com.cafeoccidente.backend.controlrecord.mapper;

import com.cafeoccidente.backend.controlrecord.dto.ControlRecordResponse;
import com.cafeoccidente.backend.controlrecord.entity.ControlRecord;
import org.springframework.stereotype.Component;

/** Unica responsabilidad: traducir la entidad ControlRecord a su DTO de respuesta. */
@Component
public class ControlRecordMapper {

    public ControlRecordResponse toResponse(ControlRecord controlRecord) {
        return new ControlRecordResponse(
                controlRecord.getId(),
                controlRecord.getAgency().getId(),
                controlRecord.getAgency().getName(),
                controlRecord.isActive(),
                controlRecord.getControlNumber(),
                controlRecord.getBaseFactor(),
                controlRecord.getBaseWithholding(),
                controlRecord.getBaseLoad(),
                controlRecord.getWithholdingPercentage(),
                controlRecord.getBaseHusk(),
                controlRecord.getAvgHuskPercentage(),
                controlRecord.getPurchasePoint(),
                controlRecord.getPrefix(),
                controlRecord.getCosts(),
                controlRecord.getSampleSize(),
                controlRecord.getExcelsoKg(),
                controlRecord.getGreenCoffeePercentage(),
                controlRecord.getSpecialtyThreshold(),
                controlRecord.getAssociatePercentage(),
                controlRecord.getNonAssociateDiscount(),
                controlRecord.getTrustedId(),
                controlRecord.getDianResolution(),
                controlRecord.getResolutionDate(),
                controlRecord.getResolutionFrom(),
                controlRecord.getResolutionTo(),
                controlRecord.getValidity());
    }
}
