package com.cafeoccidente.backend.users.dto;

import com.cafeoccidente.backend.permissions.entity.Screen;
import java.util.List;

public record LoginResponse(
        String token, String username, String roleName, Long agencyId, String agencyName, List<Screen> screens) {
}
