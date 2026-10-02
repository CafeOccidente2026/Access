package com.cafeoccidente.backend.purchases.annulment.dto;

import com.cafeoccidente.backend.purchases.annulment.repository.AnnulmentModule;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Una compra en la ficha de "CONSULTA COMPRAS PARA ANULAR" (Cns_Compras), solo lectura. */
public record AnnulmentCandidate(
        AnnulmentModule module,
        Long id,
        Long agencyId,
        String agencyName,
        String prefix,
        Integer invoiceNumber,
        LocalDate purchaseDate,
        String fundCode,
        String idNumber,
        String firstName,
        String lastName,
        String specialType,
        BigDecimal netKg,
        BigDecimal netToPay,
        String paymentMethod,
        String status,
        boolean exported,
        Instant annulledAt) {
}
