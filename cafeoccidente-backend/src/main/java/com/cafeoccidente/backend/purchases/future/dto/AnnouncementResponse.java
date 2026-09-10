package com.cafeoccidente.backend.purchases.future.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AnnouncementResponse(
        Long id,
        String announcementNumber,
        LocalDate announcementDate,
        BigDecimal basePriceLoad,
        Long agencyId,
        Long fundId) {
}
