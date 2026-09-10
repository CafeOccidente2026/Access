package com.cafeoccidente.backend.users.dto;

public record LoginResponse(String token, String username, String roleName) {
}
