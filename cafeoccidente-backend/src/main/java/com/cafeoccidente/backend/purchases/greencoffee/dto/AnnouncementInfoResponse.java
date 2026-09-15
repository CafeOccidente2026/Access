package com.cafeoccidente.backend.purchases.greencoffee.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Autocompletado de Cod Prod + anuncio vigente para la agencia del usuario, Fondo "RP" y Especial
 * "CV" (ambos fijos en VERDES - ver GreenCoffeePurchase). A diferencia de Cafe Seco, no hace falta
 * que el cliente mande fundId/specialType: siempre son los mismos.
 */
public record AnnouncementInfoResponse(
        Long fundId,
        String productCode,
        String announcementNumber,
        LocalDate announcementDate,
        BigDecimal basePriceLoad,
        BigDecimal defectiveUnitPrice,
        BigDecimal healthyUnitPrice,
        BigDecimal bonus,
        BigDecimal costs) {
}
