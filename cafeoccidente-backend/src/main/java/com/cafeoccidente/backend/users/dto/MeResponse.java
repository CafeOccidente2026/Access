package com.cafeoccidente.backend.users.dto;

import com.cafeoccidente.backend.permissions.entity.Screen;
import java.util.List;

/** Sesion actual: usuario, rol, agencia de trabajo (RF-05.1) y pantallas concedidas (RF-02). */
public record MeResponse(String username, String roleName, Long agencyId, String agencyName, List<Screen> screens) {
}
