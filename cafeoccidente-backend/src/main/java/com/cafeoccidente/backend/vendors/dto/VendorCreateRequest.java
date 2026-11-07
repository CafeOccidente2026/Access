package com.cafeoccidente.backend.vendors.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * "Catalogar Terceros" (RF-28.1): legalPerson = true es la persona juridica (NIT, Razon Social y
 * Clasificacion, RN-28.1.5); false la persona natural. Los dos llevan Sexo, Tel/Cel, Direccion,
 * Codigo Postal y Email. La agencia la pone el servidor: es la agencia de trabajo (RF-05.1).
 */
public record VendorCreateRequest(
        boolean legalPerson,
        @NotBlank @Size(max = 30) String idNumber,
        @Size(max = 100) String firstName,
        @Size(max = 100) String secondName,
        @Size(max = 100) String lastName,
        @Size(max = 100) String secondLastName,
        /** M, F o E (E = Empresa). */
        @Pattern(regexp = "[FME]") String sex,
        @Size(max = 30) String phone,
        @Size(max = 200) String address,
        @Size(max = 10) String postalCode,
        @Email @Size(max = 150) String email,
        @Size(max = 100) String businessName,
        @Pattern(regexp = "ASOCIACION|FUNDACION") String classification) {
}
