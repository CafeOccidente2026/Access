package com.cafeoccidente.backend.controlrecord.entity;

import com.cafeoccidente.backend.purchases.shared.entity.Agency;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * Parametros generales de una agencia (pantalla "RegControl" en Access).
 * Fuente de los porcentajes/bases usados en las formulas de compras (Retefuente, aportes, etc).
 * Solo se espera un registro activo por agencia a la vez (ver ControlRecordService.getActive()).
 */
@Entity
@Table(name = "registros_control")
@Getter
@Setter
public class ControlRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "agencia_id", nullable = false)
    private Agency agency;

    @Column(name = "activo", nullable = false)
    private boolean active = true;

    @Column(name = "numero_registro_control", nullable = false)
    private Integer controlNumber;

    /**
     * Cruce heredado de la carga de Access, pendiente hasta HU-07: el campo Java se llama baseFactor
     * pero guarda KgExcelso (70) y el Factor lo usa como tal. La columna ya se llama por lo que guarda.
     */
    @Column(name = "kg_excelso", nullable = false)
    private Integer baseFactor;

    @Column(name = "base_retencion", nullable = false, precision = 15, scale = 2)
    private BigDecimal baseWithholding;

    @Column(name = "base_carga", nullable = false)
    private Integer baseLoad;

    @Column(name = "porcentaje_retencion", nullable = false, precision = 5, scale = 2)
    private BigDecimal withholdingPercentage;

    @Column(name = "base_pasilla", nullable = false, precision = 10, scale = 2)
    private BigDecimal baseHusk;

    @Column(name = "porc_kg_pasilla_promedio", nullable = false, precision = 5, scale = 2)
    private BigDecimal avgHuskPercentage;

    @Column(name = "punto_compra", nullable = false)
    private String purchasePoint;

    @Column(name = "prefijo", nullable = false)
    private String prefix;

    @Column(name = "costos", nullable = false, precision = 15, scale = 2)
    private BigDecimal costs;

    @Column(name = "tamano_muestra", nullable = false, precision = 10, scale = 2)
    private BigDecimal sampleSize;

    /** Copia de KgExcelso que no se usa en ningun calculo; HU-07 decide si se borra o es el umbral (Q15). */
    @Column(name = "kg_excelso_copia", nullable = false, precision = 10, scale = 2)
    private BigDecimal excelsoKg;

    @Column(name = "porcentaje_cafe_verde", nullable = false, precision = 5, scale = 2)
    private BigDecimal greenCoffeePercentage;

    /** Cruce pendiente hasta HU-07: specialtyThreshold guarda FactorBase (94) y el Vr. Kilo lo usa como tal. */
    @Column(name = "factor_base", nullable = false, precision = 5, scale = 2)
    private BigDecimal specialtyThreshold;

    @Column(name = "porc_aportes_asociados", nullable = false, precision = 5, scale = 2)
    private BigDecimal associatePercentage;

    @Column(name = "porc_desc_coop_no_asociados", nullable = false, precision = 5, scale = 2)
    private BigDecimal nonAssociateDiscount;

    @Column(name = "cedula_fiel", nullable = false)
    private String trustedId;

    @Column(name = "resolucion_dian", nullable = false)
    private String dianResolution;

    @Column(name = "fecha_resolucion", nullable = false)
    private LocalDate resolutionDate;

    @Column(name = "desde_resolucion", nullable = false)
    private Integer resolutionFrom;

    @Column(name = "hasta_resolucion", nullable = false)
    private Integer resolutionTo;

    @Column(name = "vigencia", nullable = false)
    private Integer validity;
}
