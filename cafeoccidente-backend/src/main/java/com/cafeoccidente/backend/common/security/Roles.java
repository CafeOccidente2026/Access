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

    private Roles() {
    }
}
