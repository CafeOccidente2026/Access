package com.cafeoccidente.backend.purchases.shared.dto;

public record GrowerResponse(
        Long id,
        String idNumber,
        String firstName,
        String secondName,
        String lastName,
        String secondLastName,
        String address,
        String phone,
        String growerType,
        boolean active,
        boolean deceased,
        boolean withdrawn) {
}
