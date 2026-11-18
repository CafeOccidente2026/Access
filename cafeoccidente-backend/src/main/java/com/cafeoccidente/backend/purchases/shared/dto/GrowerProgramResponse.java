package com.cafeoccidente.backend.purchases.shared.dto;

/** Programa NESS del caficultor; cedula y nombres son los de NESS (bloque Informacion Federacion, C3). */
public record GrowerProgramResponse(String programa, String cupo, String cedula, String nombres) {
}
