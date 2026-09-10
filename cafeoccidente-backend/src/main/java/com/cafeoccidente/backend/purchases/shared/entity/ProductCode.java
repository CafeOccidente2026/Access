package com.cafeoccidente.backend.purchases.shared.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

/**
 * Codigo de producto (Cod_Prod) resultante de combinar el tipo especial de cafe
 * (Cuadro_combinado61 en el formulario Access original) con el Fondo (Cuadro_combinado37).
 */
@Entity
@Getter
@Setter
public class ProductCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "special_type", nullable = false)
    private String specialType;

    @ManyToOne
    @JoinColumn(name = "fund_id", nullable = false)
    private Fund fund;

    @Column(nullable = false, unique = true)
    private String code;
}
