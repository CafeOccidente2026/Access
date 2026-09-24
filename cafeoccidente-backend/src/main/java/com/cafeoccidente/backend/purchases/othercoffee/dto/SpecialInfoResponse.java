package com.cafeoccidente.backend.purchases.othercoffee.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Autocompletado al confirmar Fondo+Especial: Cod Prod + los datos del anuncio vigente. */
public record SpecialInfoResponse(
        String productCode,
        String announcementNumber,
        LocalDate announcementDate,
        BigDecimal basePriceLoad,
        BigDecimal defectiveUnitPrice,
        BigDecimal healthyUnitPrice,
        BigDecimal bonus,
        BigDecimal costs) {
}
