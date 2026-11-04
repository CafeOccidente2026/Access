package com.cafeoccidente.backend.permissions.dto;

import com.cafeoccidente.backend.permissions.entity.Screen;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** RN-02.2: el usuario y la(s) pantalla(s) a las que se le da acceso. */
public record PermissionGrantRequest(@NotNull Long userId, @NotEmpty List<@NotNull Screen> screens) {
}
