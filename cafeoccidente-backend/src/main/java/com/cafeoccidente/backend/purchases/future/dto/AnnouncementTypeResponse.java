package com.cafeoccidente.backend.purchases.future.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Una fila del listado "Actualizar" (HU-11 CA-1): el tipo de cafe y su anuncio vigente. Los valores son
 * null si el tipo todavia no tiene anuncio.
 */
public record AnnouncementTypeResponse(
        String specialType,
        BigDecimal basePriceLoad,
        BigDecimal specialSurcharge,
        BigDecimal defectiveUnitPrice,
        LocalDate announcementDate) {
}
