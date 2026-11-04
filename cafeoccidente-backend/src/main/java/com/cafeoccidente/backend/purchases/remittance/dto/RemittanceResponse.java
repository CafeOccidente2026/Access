package com.cafeoccidente.backend.purchases.remittance.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** Una remesa: su numero PREFIJO-FONDO-CONSECUTIVO, sus empaques y cuantas facturas y kilos lleva. */
public record RemittanceResponse(
        Long id,
        String number,
        String fundCode,
        String specialType,
        Integer packagingQuantity,
        String packagingType,
        String status,
        Instant createdAt,
        int purchases,
        BigDecimal netKg) {
}
