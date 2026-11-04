package com.cafeoccidente.backend.purchases.shared.dto;

/**
 * Agencia para las listas del Administrador. hasControlRecord avisa si ya puede operar (sin Registro
 * de Control no se registran compras ni suministros); suggestedPrefix es el prefijo que traia de
 * Access para prellenar su Registro de Control (D2), null si no hay.
 */
public record AgencyResponse(Long id, String name, boolean hasControlRecord, String suggestedPrefix) {
}
