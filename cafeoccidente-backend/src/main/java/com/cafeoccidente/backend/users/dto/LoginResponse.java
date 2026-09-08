package com.cafeoccidente.backend.users.dto;

import com.cafeoccidente.backend.users.entity.Role;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        String username,
        Role role) {
}
