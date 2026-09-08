package com.cafeoccidente.backend.users.entity;

// Solo existen 2 roles fijos en el negocio, por lo que se modela como enum
// simple embebido en User (columna string) en vez de una tabla role aparte:
// evita una tabla y un mapeo extra para un catalogo que no va a crecer.
public enum Role {
    ADMIN,
    PURCHASE_AGENT
}
