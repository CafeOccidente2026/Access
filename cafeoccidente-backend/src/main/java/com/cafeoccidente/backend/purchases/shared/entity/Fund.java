package com.cafeoccidente.backend.purchases.shared.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Fondo de compra (ej: RP = Recursos Propios, LF = Linea de Formacion). */
@Entity
@Table(name = "fondos")
@Getter
@Setter
public class Fund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "codigo", nullable = false, unique = true)
    private String code;

    @Column(name = "nombre", nullable = false)
    private String name;

    @Column(name = "activo", nullable = false)
    private boolean active = true;
}
