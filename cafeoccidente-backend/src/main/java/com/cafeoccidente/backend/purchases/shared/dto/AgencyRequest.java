package com.cafeoccidente.backend.purchases.shared.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** HU-09: crear una agencia desde Usuarios. */
public record AgencyRequest(@NotBlank @Size(max = 100) String name) {
}
