package com.cafeoccidente.backend.users.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UserRequest(
        @NotBlank String username,
        @NotBlank @Size(min = 8, message = "La contrasena debe tener al menos 8 caracteres") String password,
        @NotNull Long roleId,
        @NotNull Long municipalityId) {
}
