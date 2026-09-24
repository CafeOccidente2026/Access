package com.cafeoccidente.backend.inventory.dto;

import java.time.LocalDate;
import java.util.List;

public record RemissionResponse(
        Long id,
        Integer remissionNumber,
        Long agencyId,
        String agencyName,
        LocalDate remissionDate,
        String destination,
        String conductorIdNumber,
        String conductorName,
        String transportCompany,
        String vehiclePlate,
        boolean exported,
        List<RemissionLineResponse> lines) {
}
