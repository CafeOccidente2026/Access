package com.cafeoccidente.backend.common.security;

/** Roles del RF (RF-01) y las expresiones de @PreAuthorize de la matriz rol x endpoint. */
public final class Roles {

    public static final String ADMINISTRADOR = "ADMINISTRADOR";
    public static final String USUARIO = "USUARIO";
    public static final String OPERACIONES = "OPERACIONES";

    public static final String ADMIN = "hasRole('" + ADMINISTRADOR + "')";
    public static final String ADMIN_USER = "hasAnyRole('" + ADMINISTRADOR + "', '" + USUARIO + "')";
    /** Actualizar Anuncios (RF-08, RF-09): el rol Operaciones solo entra aca (RN-05.3). */
    public static final String ADMIN_OPS = "hasAnyRole('" + ADMINISTRADOR + "', '" + OPERACIONES + "')";
    public static final String ANY = "hasAnyRole('" + ADMINISTRADOR + "', '" + USUARIO + "', '" + OPERACIONES + "')";

    // Pantallas que el Administrador puede conceder (RF-02): su rol o el permiso vigente (Screen).
    public static final String ANNUL = ADMIN + " or hasAuthority('SCREEN_ANULAR_DOCUMENTO')";
    public static final String CONTROL_RECORD = ADMIN + " or hasAuthority('SCREEN_REGISTRO_CONTROL')";
    public static final String HISTORY = ADMIN + " or hasAuthority('SCREEN_HISTORIAL')";
    public static final String ANNOUNCEMENTS = ADMIN_OPS + " or hasAuthority('SCREEN_ACTUALIZAR_ANUNCIOS')";

    private Roles() {
    }
}
