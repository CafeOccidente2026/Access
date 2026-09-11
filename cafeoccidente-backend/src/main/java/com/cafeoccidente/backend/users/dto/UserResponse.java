package com.cafeoccidente.backend.users.dto;

public record UserResponse(
        Long id,
        String username,
        boolean active,
        Long roleId,
        String roleName,
        Long agencyId,
        String agencyName) {
}
