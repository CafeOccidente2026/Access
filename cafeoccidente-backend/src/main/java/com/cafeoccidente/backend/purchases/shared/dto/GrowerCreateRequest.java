package com.cafeoccidente.backend.purchases.shared.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Alta rápida de un conductor (Grower sin datos de caficultor) desde Registrar Salidas. */
public record GrowerCreateRequest(
        @NotBlank String idNumber,
        @NotBlank String firstName,
        String secondName,
        @NotBlank String lastName,
        String secondLastName,
        @NotNull Long agencyId,
        String transportCompany,
        String vehiclePlate) {
}
