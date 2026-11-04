package com.cafeoccidente.backend.permissions.service;

import com.cafeoccidente.backend.permissions.dto.PermissionResponse;
import com.cafeoccidente.backend.permissions.entity.Screen;
import java.util.List;

/** Permisos temporales por pantalla (RF-02, HU-03). Conceder y retirar quedan en el Historial. */
public interface PermissionService {

    List<PermissionResponse> list(Long userId);

    /** RN-02.2. Una pantalla que el usuario ya tiene no se duplica. */
    List<PermissionResponse> grant(Long userId, List<Screen> screens);

    /** RN-02.3: el usuario vuelve a su acceso normal segun su rol. */
    void revoke(Long userId, Screen screen);
}
