package com.cafeoccidente.backend.supplies.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Alta de Empaque: Entradas (Empaque Suministros) o Salidas (Prestamo Empaques) en quantity. */
public record PackagingEntryRequest(
        @NotBlank @Size(max = 30) String transactionId,
        Long agencyId,
        @NotBlank String packagingType,
        LocalDate entryDate,
        @NotBlank @Pattern(regexp = "[0-9]{1,20}") String idNumber,
        @Size(max = 255) String detail,
        @NotNull @Positive Integer quantity) {
}
