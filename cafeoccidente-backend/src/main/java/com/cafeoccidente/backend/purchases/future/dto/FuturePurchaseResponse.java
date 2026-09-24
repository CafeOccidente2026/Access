package com.cafeoccidente.backend.purchases.future.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FuturePurchaseResponse(
        Long id,
        Long agencyId,
        String agencyName,
        String announcementNumber,
        LocalDate announcementDate,
        String idNumber,
        String firstName,
        String lastName,
        String growerType,
        String address,
        String specialType,
        BigDecimal announcedKg,
        BigDecimal remainingKg,
        LocalDate deliveryDate,
        String finca,
        String municipality,
        String vereda,
        BigDecimal healthyUnitPrice,
        BigDecimal defectiveUnitPrice,
        BigDecimal bonus,
        BigDecimal costs,
        BigDecimal qualityIncrement,
        BigDecimal basePriceLoad) {
}
