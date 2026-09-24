package com.cafeoccidente.backend.users.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record UserRequest(
        @NotBlank String username,
        @NotBlank
                @Pattern(
                        regexp = "^(?=.*[A-Za-z])(?=.*\\d).{4,}$",
                        message = "La contrasena debe tener minimo 4 caracteres y combinar letras y numeros")
                String password,
        @NotNull Long roleId,
        @NotNull Long agencyId) {
}
