package com.cafeoccidente.backend.purchases.future.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * @param deliveredKg Entregados: suma real de Kilos Netos ya comprados contra este anuncio.
 * @param balance Saldo: assignedQuota - deliveredKg (puede dar negativo si se excedio).
 * @param displayNumber el anuncio con el prefijo del punto ("SDTA-3738"), para mostrarlo como en las demas pantallas (R4).
 */
public record AnnouncementQuotaResponse(
        Long id,
        Long agencyId,
        Integer announcementNumber,
        LocalDate announcementDate,
        String specialType,
        BigDecimal assignedQuota,
        BigDecimal deliveredKg,
        BigDecimal balance,
        String displayNumber) {
}
