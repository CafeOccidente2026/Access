package com.cafeoccidente.backend.purchases.shared.dto;

import java.time.Instant;
import java.time.LocalDate;

/** Conductor para "Ingresar Conductores" (Form_Conductores / Form_Conductores Actualizacion). */
public record ConductorResponse(
        String idNumber,
        String firstName,
        String lastName,
        String agencyName,
        String address,
        LocalDate affiliationDate,
        Instant updatedAt,
        String transportCompany,
        String vehiclePlate) {
}
