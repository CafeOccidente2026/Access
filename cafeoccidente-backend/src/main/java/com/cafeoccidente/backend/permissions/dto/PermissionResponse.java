package com.cafeoccidente.backend.permissions.dto;

import com.cafeoccidente.backend.permissions.entity.Screen;
import java.time.OffsetDateTime;

/** Permiso vigente de un usuario. */
public record PermissionResponse(Screen screen, OffsetDateTime grantedAt) {
}
