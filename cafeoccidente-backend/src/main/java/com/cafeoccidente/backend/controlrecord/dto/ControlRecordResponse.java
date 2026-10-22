package com.cafeoccidente.backend.controlrecord.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ControlRecordResponse(
        Long id,
        Long agencyId,
        String agencyName,
        boolean active,
        Integer controlNumber,
        BigDecimal baseFactor,
        BigDecimal baseWithholding,
        Integer baseLoad,
        BigDecimal withholdingPercentage,
        BigDecimal baseHusk,
        BigDecimal avgHuskPercentage,
        String purchasePoint,
        String prefix,
        BigDecimal costs,
        BigDecimal sampleSize,
        Integer excelsoKg,
        BigDecimal greenCoffeePercentage,
        BigDecimal associatePercentage,
        BigDecimal nonAssociateDiscount,
        String trustedId,
        String dianResolution,
        LocalDate resolutionDate,
        Integer resolutionFrom,
        Integer resolutionTo,
        Integer validity) {
}
