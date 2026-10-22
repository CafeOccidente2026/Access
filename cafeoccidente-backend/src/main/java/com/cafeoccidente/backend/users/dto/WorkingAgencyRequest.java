package com.cafeoccidente.backend.users.dto;

import jakarta.validation.constraints.NotNull;

/** Boton Agencias del menu principal (RN-05.1.1). */
public record WorkingAgencyRequest(@NotNull Long agencyId) {
}
