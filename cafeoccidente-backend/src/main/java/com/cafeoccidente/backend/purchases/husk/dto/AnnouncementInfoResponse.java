package com.cafeoccidente.backend.purchases.husk.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Autocompletado de Cod Prod + anuncio vigente para la agencia del usuario, Fondo "RP" y Especial
 * "PASILLA" (ambos fijos en la practica - ver HuskPurchase).
 */
public record AnnouncementInfoResponse(
        Long fundId,
        String productCode,
        String announcementNumber,
        LocalDate announcementDate,
        BigDecimal basePriceDryLoad,
        BigDecimal pointPrice,
        BigDecimal costs) {
}
