package com.cafeoccidente.backend.vendors.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * "Ingresar Vendedores": association = true es la pantalla de 6 items (Asociaciones1: todo el nombre
 * en firstName, Sexo "E"); false la de 13 items (formulario Vendedores, persona). agencyId solo lo
 * usa ADMIN: USER queda siempre con la agencia de su sesion.
 */
public record VendorCreateRequest(
        boolean association,
        @NotBlank @Size(max = 30) String idNumber,
        @NotBlank @Size(max = 100) String firstName,
        @Size(max = 100) String secondName,
        @Size(max = 100) String lastName,
        @Size(max = 100) String secondLastName,
        Long agencyId,
        /** Combo de Access con LimitToList: F, M o E. */
        @Pattern(regexp = "[FME]") String sex,
        @Size(max = 30) String phone,
        @Size(max = 200) String address,
        @Size(max = 10) String postalCode,
        @Email @Size(max = 150) String email) {
}
