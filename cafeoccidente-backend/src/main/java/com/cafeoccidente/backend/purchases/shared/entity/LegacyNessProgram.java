package com.cafeoccidente.backend.purchases.shared.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * Fila de la tabla staging_legacy_ness (cargada por scripts/migrate_eltambo.py desde ness_migrar.csv,
 * fuera de Flyway - es staging de solo lectura, no una tabla propia de la app). Cedula/cupo llegan
 * como texto con sufijo ".0" (artefacto del CSV origen), normalizados al consultar/leer.
 *
 * Cubre solo 3 de ~20 Especiales del formulario de Compras (ver GrowerServiceImpl.findProgram):
 * NESPRESSO - FTUSA, NESPRESSO LATE HARVEST - FTUSA, REGIONAL NARIÑO 4C. Evidencia VBA:
 * Form_COMPRAS.bas Cuadro_combinado61_AfterUpdate (macros "Abrir NESS/NESSLH/RN4C pcompras").
 */
@Entity
@Getter
@Table(name = "staging_legacy_ness")
public class LegacyNessProgram {

    @Id
    private Integer id;

    private String cedula;

    private String nombres;

    private String programa;

    private String cupo;
}
