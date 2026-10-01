package com.cafeoccidente.backend.supplies.dto;

import java.time.LocalDate;

/** Registro guardado. Nombres solo en Prestamo Empaques (para la constancia "Prest Empaques"). */
public record EntryResponse(
        Long id,
        String transactionId,
        String agencyName,
        LocalDate entryDate,
        String idNumber,
        String firstNames,
        String lastNames) {
}
