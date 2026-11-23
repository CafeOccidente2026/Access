package com.cafeoccidente.backend.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.List;

public record RemissionRequest(
        @NotNull LocalDate remissionDate,
        String destination,
        /** Cédula del conductor (= Grower, ver Remission). Opcional. */
        String conductorIdNumber,
        @NotEmpty List<@Valid RemissionLineRequest> lines,
        /** Empaques que salen con la remision (RN-11.9): sugerido = suma de sacos, editable. */
        @NotNull @Positive Integer packagingQuantity,
        /** NUEVO o USADO. */
        @NotBlank String packagingType) {
}
