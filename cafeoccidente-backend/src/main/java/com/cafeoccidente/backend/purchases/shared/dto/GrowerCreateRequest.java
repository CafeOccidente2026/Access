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
        String vehiclePlate,
        /** Opcional: el alta rapida de Registrar Salidas no la envia (queda vacia como antes). */
        String address) {

    /** Alta rapida sin direccion (firma anterior a "Ingresar Conductores"). */
    public GrowerCreateRequest(
            String idNumber, String firstName, String secondName, String lastName, String secondLastName,
            Long agencyId, String transportCompany, String vehiclePlate) {
        this(idNumber, firstName, secondName, lastName, secondLastName, agencyId, transportCompany, vehiclePlate, null);
    }
}
