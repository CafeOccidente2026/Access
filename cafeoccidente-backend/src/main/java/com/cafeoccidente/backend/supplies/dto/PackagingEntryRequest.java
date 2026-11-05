package com.cafeoccidente.backend.supplies.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Alta de Empaque: Entradas (Suministro Empaques) o Salidas (Prestamo Empaques) en quantity. El Id y la fecha los pone
 * el servidor (RN-27.6.1, RN-27.7.1).
 */
public record PackagingEntryRequest(
        @NotBlank String packagingType,
        @NotBlank @Pattern(regexp = "[0-9]{1,20}") String idNumber,
        @Size(max = 255) String detail,
        @NotNull @Positive Integer quantity) {
}
