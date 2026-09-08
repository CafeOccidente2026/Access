package com.cafeoccidente.backend.users.dto;

import com.cafeoccidente.backend.users.entity.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// Se usa para crear un usuario nuevo (solo un ADMIN puede hacerlo, no hay
// auto-registro ni edicion todavia, asi que un unico DTO alcanza).
public record UserRequest(
        @NotBlank String username,
        @NotBlank String password,
        @NotBlank String fullName,
        @NotNull Role role) {
}
