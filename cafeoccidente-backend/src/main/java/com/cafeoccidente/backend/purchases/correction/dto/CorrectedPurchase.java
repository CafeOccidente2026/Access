package com.cafeoccidente.backend.purchases.correction.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Lo que la correccion deja fijo de la compra (fondo, especial, anuncio, vendedor, fecha) y lo que ya pesaba. */
public record CorrectedPurchase(
        Long agencyId,
        Long fundId,
        String specialType,
        String announcementNumber,
        Integer invoiceNumber,
        LocalDate purchaseDate,
        String idNumber,
        String firstName,
        String lastName,
        String growerType,
        String address,
        String cellphone,
        boolean withholdingExempt,
        BigDecimal netKg,
        BigDecimal grossValue,
        BigDecimal withholding) {
}
