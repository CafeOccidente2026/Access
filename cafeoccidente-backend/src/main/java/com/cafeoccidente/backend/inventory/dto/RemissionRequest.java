package com.cafeoccidente.backend.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

public record RemissionRequest(
        @NotNull Long agencyId,
        @NotNull LocalDate remissionDate,
        String destination,
        /** Cédula del conductor (= Grower, ver Remission). Opcional. */
        String conductorIdNumber,
        @NotEmpty List<@Valid RemissionLineRequest> lines) {
}
