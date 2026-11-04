package com.cafeoccidente.backend.purchases.shared.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Agencia/punto de compra (tambien la sede de un usuario - antes modelada por separado como
 * "Municipality", consolidada aqui porque son el mismo concepto de negocio).
 */
@Entity
@Table(name = "agencias")
@Getter
@Setter
public class Agency {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Nombre visible (ej: "Buesaco"). */
    @Column(name = "nombre", nullable = false, unique = true)
    private String name;

    /**
     * Valor exacto que Access escribe en el campo "agencia" de COMPRAS/Anuncios/RegControl
     * (ej: "BUESACO1 OCCIDENTE" para el punto de compra "Buesaco"). No siempre coincide con el
     * nombre visible.
     */
    @Column(name = "nombre_access", unique = true)
    private String accessAgencyValue;

    @Column(name = "activa", nullable = false)
    private boolean active = true;
}
