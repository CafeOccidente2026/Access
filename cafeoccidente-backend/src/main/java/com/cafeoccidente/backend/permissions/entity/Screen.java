package com.cafeoccidente.backend.permissions.entity;

/**
 * Pantallas restringidas que el Administrador puede conceder a un Usuario u Operaciones (RF-02,
 * ck_permisos_pantalla). Quedan fuera Permisos, Usuarios y Agencias: darian poderes de Administrador.
 * La etiqueta es la que queda en el Historial.
 */
public enum Screen {
    ANULAR_DOCUMENTO("Anular Documento"),
    ACCESO_TABLAS("Acceso a Tablas"),
    ACTUALIZAR_ANUNCIOS("Actualizar Anuncios"),
    REGISTRO_CONTROL("Registro de Control"),
    HISTORIAL("Historial");

    /** Prefijo de la authority de Spring Security: SCREEN_ANULAR_DOCUMENTO, etc. */
    public static final String AUTHORITY_PREFIX = "SCREEN_";

    private final String label;

    Screen(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
