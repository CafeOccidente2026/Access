package com.cafeoccidente.backend.users.dto;

/** Sesion actual: usuario, rol y agencia de trabajo (RF-05.1). */
public record MeResponse(String username, String roleName, Long agencyId, String agencyName) {
}
