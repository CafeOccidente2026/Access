package com.cafeoccidente.backend.users.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Edicion de un usuario por el Administrador (fase 3, C4): reasignar agencia, cambiar rol,
 * activar/desactivar y, si se manda, cambiar la contrasena. La cedula solo se puede agregar si el
 * usuario aun no la tiene (los creados antes de V7).
 */
public record UserUpdateRequest(
        @NotNull Long roleId,
        @NotNull Long agencyId,
        @NotNull Boolean active,
        @Pattern(regexp = "^$|" + UserRequest.SECURE_KEY_PATTERN, message = UserRequest.SECURE_KEY_MESSAGE)
                String password,
        @Size(max = 30) @Pattern(regexp = "^$|\\d+", message = "La cedula solo acepta numeros") String idNumber) {
}
