package com.cafeoccidente.backend.users.dto;

import com.cafeoccidente.backend.users.entity.Role;
import java.time.Instant;

public record UserResponse(
        Long id,
        String username,
        String fullName,
        Role role,
        boolean active,
        Instant createdAt) {
}
