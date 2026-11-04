package com.cafeoccidente.backend.purchases.remittance.dto;

import com.cafeoccidente.backend.purchases.annulment.repository.AnnulmentModule;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Una factura pendiente de remesar (formulario continuo de Genera Plano en Access). */
public record RemittancePurchase(
        AnnulmentModule module,
        Long id,
        String invoice,
        LocalDate purchaseDate,
        String specialType,
        String idNumber,
        String sellerName,
        BigDecimal netKg,
        BigDecimal grossValue) {
}
