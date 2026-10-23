package com.cafeoccidente.backend.users.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** HU-08: alta de usuario (RF-07). La cedula es la del fiel de compra y tambien queda en terceros. */
public record UserRequest(
        @NotBlank String username,
        @NotBlank
                @Size(max = 30)
                @Pattern(regexp = "\\d+", message = "La cedula solo acepta numeros")
                String idNumber,
        @NotBlank
                @Pattern(
                        regexp = UserRequest.SECURE_KEY_PATTERN,
                        message = UserRequest.SECURE_KEY_MESSAGE)
                String password,
        @NotNull Long roleId,
        @NotNull Long agencyId) {

    /** RN-07.1: minimo 4 caracteres, combinando letras y numeros. */
    public static final String SECURE_KEY_PATTERN = "^(?=.*[A-Za-z])(?=.*\\d).{4,}$";
    public static final String SECURE_KEY_MESSAGE = "La contrasena debe tener minimo 4 caracteres y combinar letras y numeros";
}
