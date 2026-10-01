package com.cafeoccidente.backend.vendors.dto;

import java.time.LocalDate;

/** Los 28 controles del formulario "Asociados" de Access (Consultar Asociados), en su orden. */
public record AssociateResponse(
        String idNumber,
        String firstName,
        String secondName,
        String lastName,
        String secondLastName,
        String agencyName,
        LocalDate birthDate,
        String birthPlace,
        String maritalStatus,
        String coffeeIdCard,
        String address,
        String phone,
        LocalDate affiliationDate,
        String actNumber,
        Boolean accepted,
        Boolean eligible,
        boolean withdrawn,
        boolean deceased,
        String observation,
        String growerType,
        String transportCompany,
        String vehiclePlate,
        Boolean exported,
        Boolean isAssociation,
        Boolean isNew,
        String cityCode,
        String sex,
        String country) {
}
