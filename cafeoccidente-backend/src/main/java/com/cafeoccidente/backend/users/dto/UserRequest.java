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
                        regexp = UserRequest.PASSWORD_PATTERN,
                        message = UserRequest.PASSWORD_MESSAGE)
                String password,
        @NotNull Long roleId,
        @NotNull Long agencyId) {

    /** RN-07.1 con el minimo subido a 8 (decision del 13-nov): letras y numeros. Las claves ya guardadas siguen sirviendo. */
    public static final String PASSWORD_PATTERN = "^(?=.*[A-Za-z])(?=.*\\d).{8,}$";
    public static final String PASSWORD_MESSAGE = "La contrasena debe tener minimo 8 caracteres y combinar letras y numeros";
}
