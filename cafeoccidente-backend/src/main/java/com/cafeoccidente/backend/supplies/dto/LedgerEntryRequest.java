package com.cafeoccidente.backend.supplies.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Alta de Caja / Caja Menor / Suministros. Cada pantalla usa solo los campos que tiene en Access; el
 * resto (fecha de hoy, Fondo RP y Forma de Pago fijos) lo pone el servicio, no el cliente.
 * agencyId: solo ADMIN lo elige; a USER se le fuerza la de su sesion.
 */
public record LedgerEntryRequest(
        @NotBlank @Size(max = 30) String transactionId,
        Long agencyId,
        String fundCode,
        LocalDate entryDate,
        @NotBlank @Pattern(regexp = "[0-9]{1,20}") String idNumber,
        @Size(max = 255) String detail,
        @NotNull @Positive BigDecimal amount,
        String paymentMethod,
        @PositiveOrZero Integer checkNumber) {
}
