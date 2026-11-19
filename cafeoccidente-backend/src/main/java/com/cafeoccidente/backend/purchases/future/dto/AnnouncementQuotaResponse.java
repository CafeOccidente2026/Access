package com.cafeoccidente.backend.purchases.future.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * @param deliveredKg Entregados: kilos netos de todas las compras validas con este anuncio y especial (informativo).
 * @param quotaKg Kilos con Cupo: kilos netos de las compras de Facturar Anuncios con Cupos.
 * @param balance Saldo: assignedQuota - quotaKg.
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
        BigDecimal quotaKg,
        BigDecimal balance,
        String displayNumber) {
}
