package com.cafeoccidente.backend.purchases.future.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Campos de entrada del formulario "Compras a Futuro". A diferencia de Café Seco/Verde/Pasilla/
 * Otros, el nombre/apellidos/tipo/dirección NO se capturan a mano: el RecordSource real
 * (COMPRAS A FUTURO.txt) hace JOIN contra Asociados por cédula, así que el servidor los resuelve
 * vía {@link com.cafeoccidente.backend.purchases.shared.service.GrowerService#findByIdNumber}.
 */
public record FuturePurchaseRequest(
        @NotNull Long agencyId,
        @NotBlank String idNumber,
        @NotBlank String specialType,
        @NotNull @Positive BigDecimal announcedKg,
        @NotNull LocalDate deliveryDate,
        String finca,
        String municipality,
        String vereda) {
}
