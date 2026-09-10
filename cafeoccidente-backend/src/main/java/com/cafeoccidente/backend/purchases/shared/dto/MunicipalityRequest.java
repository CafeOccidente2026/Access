package com.cafeoccidente.backend.purchases.shared.dto;

import jakarta.validation.constraints.NotBlank;

public record MunicipalityRequest(@NotBlank String name) {
}
