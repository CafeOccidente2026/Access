package com.cafeoccidente.backend.controlrecord.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * Parametros generales de la agencia (pantalla "RegControl" en Access).
 * Fuente de los porcentajes/bases usados en las formulas de compras (Retefuente, aportes, etc).
 * Solo se espera un registro activo a la vez (ver ControlRecordService.getActive()).
 */
@Entity
@Getter
@Setter
public class ControlRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "control_number", nullable = false)
    private Integer controlNumber;

    @Column(name = "base_factor", nullable = false)
    private Integer baseFactor;

    @Column(name = "base_withholding", nullable = false, precision = 15, scale = 2)
    private BigDecimal baseWithholding;

    @Column(name = "base_load", nullable = false)
    private Integer baseLoad;

    @Column(name = "withholding_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal withholdingPercentage;

    @Column(name = "base_husk", nullable = false, precision = 10, scale = 2)
    private BigDecimal baseHusk;

    @Column(name = "avg_husk_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal avgHuskPercentage;

    @Column(name = "purchase_point", nullable = false)
    private String purchasePoint;

    @Column(nullable = false)
    private String prefix;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal costs;

    @Column(name = "sample_size", nullable = false, precision = 10, scale = 2)
    private BigDecimal sampleSize;

    @Column(name = "excelso_kg", nullable = false, precision = 10, scale = 2)
    private BigDecimal excelsoKg;

    @Column(name = "green_coffee_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal greenCoffeePercentage;

    @Column(name = "specialty_threshold", nullable = false, precision = 5, scale = 2)
    private BigDecimal specialtyThreshold;

    @Column(name = "associate_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal associatePercentage;

    @Column(name = "non_associate_discount", nullable = false, precision = 5, scale = 2)
    private BigDecimal nonAssociateDiscount;

    @Column(name = "trusted_id", nullable = false)
    private String trustedId;

    @Column(name = "dian_resolution", nullable = false)
    private String dianResolution;

    @Column(name = "resolution_date", nullable = false)
    private LocalDate resolutionDate;

    @Column(name = "resolution_from", nullable = false)
    private Integer resolutionFrom;

    @Column(name = "resolution_to", nullable = false)
    private Integer resolutionTo;

    @Column(nullable = false)
    private Integer validity;
}
