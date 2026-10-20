package com.cafeoccidente.backend.purchases.future.entity;

import com.cafeoccidente.backend.purchases.shared.entity.Agency;
import com.cafeoccidente.backend.purchases.shared.entity.Fund;
import com.cafeoccidente.backend.purchases.shared.entity.ProductCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * Liquidación de FERTIFUTURO (Form_FERTIFUTURO.bas, Castigo_lostFocus): el pago real de lo
 * comprometido en "Compras a Futuro" ({@link FuturePurchase}), con cascada de precio completa y
 * factura propia - a diferencia del compromiso, que no tiene ninguna de las dos. Ver
 * {@link com.cafeoccidente.backend.purchases.future.service.FertiFuturoPurchaseCalculator} para la
 * fórmula (usa constantes fijas del VBA, no ControlRecord - confirmado y decisión del usuario
 * 2026-09-23). NO incluye el sub-sistema de "obligación" (Form_FUTURE FERTIFUTURO.bas) - fuera de
 * esta fase.
 */
@Entity
@Table(name = "compras_ferti_futuro")
@Getter
@Setter
public class FertiFuturoPurchase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fecha_compra", nullable = false)
    private LocalDate purchaseDate;

    @Column(name = "numero_factura", nullable = false, unique = true)
    private Integer invoiceNumber;

    @ManyToOne
    @JoinColumn(name = "agencia_id", nullable = false)
    private Agency agency;

    /** RP o LF (Cuadro combinado37). */
    @ManyToOne
    @JoinColumn(name = "fondo_id", nullable = false)
    private Fund fund;

    /** Especial (Cuadro combinado61: RN/NESS/CORR/TE - "NESS" resuelve a los mismos Cod_Prod que
     *  "NESPRESSO - FTUSA" en Seco/Otros, ver migración V25). */
    @Column(name = "especial", nullable = false)
    private String specialType;

    @ManyToOne
    @JoinColumn(name = "producto_id", nullable = false)
    private ProductCode productCode;

    @Column(name = "numero_anuncio", nullable = false)
    private String announcementNumber;

    @Column(name = "fecha_anuncio", nullable = false)
    private LocalDate announcementDate;

    /** Compromiso de Compras a Futuro que esta liquidacion paga, si aplica (Form_FUTURE
     *  FERTIFUTURO.bas: Texto29_AfterUpdate descuenta esta entrega del Saldo_Kilos del compromiso -
     *  ver FertiFuturoPurchaseServiceImpl). Nullable: no toda liquidacion tiene que venir de un
     *  compromiso previo registrado en el sistema. */
    @ManyToOne
    @JoinColumn(name = "compra_anunciada_id")
    private FuturePurchase futurePurchase;

    @Column(name = "cedula", nullable = false)
    private String idNumber;

    @Column(name = "nombres", nullable = false)
    private String firstName;

    @Column(name = "apellidos", nullable = false)
    private String lastName;

    @Column(name = "tipo_vendedor", nullable = false)
    private String growerType;

    @Column(name = "direccion", nullable = false)
    private String address;

    @Column(name = "sacos", nullable = false)
    private Integer sacos;

    /** Kilos_Netos: a diferencia de Seco/Otros, es un INPUT directo aca (Sacos_AfterUpdate/
     *  Kilos_Brutos_AfterUpdate derivan Destare/Kilos_Brutos a partir de este, no al reves). */
    @Column(name = "kilos_netos", nullable = false, precision = 10, scale = 2)
    private BigDecimal netKg;

    @Column(name = "kilos_brutos", nullable = false, precision = 10, scale = 2)
    private BigDecimal grossKg;

    /** Destare = Kilos_Brutos - Kilos_Netos (Kilos_Brutos_AfterUpdate). */
    @Column(name = "destare", nullable = false, precision = 10, scale = 2)
    private BigDecimal tareKg;

    @Column(name = "peso_alm_sana", nullable = false, precision = 10, scale = 2)
    private BigDecimal healthyStoredWeight;

    /** PorcAlmSana = (W_AlmSana*100)/250 (W_AlmSana_AfterUpdate - 250 fijo, no sampleSize). */
    @Column(name = "factor", nullable = false, precision = 6, scale = 2)
    private BigDecimal healthyPercentage;

    @Column(name = "peso_tot_pasilla", nullable = false, precision = 10, scale = 2)
    private BigDecimal defectiveStoredWeight;

    @Column(name = "porc_kg_pasilla", nullable = false, precision = 6, scale = 2)
    private BigDecimal defectivePercentage;

    /** Pr_AlmSana, congelado del anuncio al momento de la liquidación. */
    @Column(name = "pr_alm_sana", nullable = false, precision = 15, scale = 2)
    private BigDecimal healthyUnitPrice;

    @Column(name = "pr_alm_defec", nullable = false, precision = 15, scale = 2)
    private BigDecimal defectiveUnitPrice;

    @Column(name = "bonificacion", nullable = false, precision = 15, scale = 2)
    private BigDecimal bonus;

    /** Castigo. */
    @Column(name = "castigo", nullable = false, precision = 15, scale = 2)
    private BigDecimal penalty;

    @Column(name = "costos", nullable = false, precision = 15, scale = 2)
    private BigDecimal costs;

    /** VrIncCalidad: tasa cruda del anuncio (Announcement.qualityIncrement), congelada aca. */
    @Column(name = "pr_inc_calidad", nullable = false, precision = 15, scale = 2)
    private BigDecimal qualityIncrementRate;

    /** IncCalidad calculado: max(0, PorcAlmSana - 75) * VrIncCalidad (W_AlmSana_AfterUpdate). */
    @Column(name = "vr_inc_calidad", nullable = false, precision = 15, scale = 2)
    private BigDecimal qualityIncrementAmount;

    @Column(name = "vr_kilo", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "vr_bruto", nullable = false, precision = 15, scale = 2)
    private BigDecimal grossValue;

    @Column(name = "vr_inventario", nullable = false, precision = 15, scale = 2)
    private BigDecimal inventoryValue;

    @Column(name = "aporte_asociado", nullable = false, precision = 15, scale = 2)
    private BigDecimal associateContribution;

    @Column(name = "descuento_cooperativo", nullable = false, precision = 15, scale = 2)
    private BigDecimal cooperativeDiscount;

    @Column(name = "exento_retencion", nullable = false)
    private boolean withholdingExempt;

    @Column(name = "retefuente", nullable = false, precision = 15, scale = 2)
    private BigDecimal withholding;

    @Column(name = "descuento_financiero", nullable = false, precision = 15, scale = 2)
    private BigDecimal freightDiscount;

    @Column(name = "otros_descuentos", nullable = false, precision = 15, scale = 2)
    private BigDecimal otherDiscounts;

    @Column(name = "neto_a_pagar", nullable = false, precision = 15, scale = 2)
    private BigDecimal netToPay;

    @Column(name = "forma_pago", nullable = false)
    private String paymentMethod;

    @Column(name = "numero_cheque")
    private String checkNumber;

    @Column(name = "creado_por_usuario_id", nullable = false)
    private Long createdByUserId;

    @Column(name = "creado_en", nullable = false)
    private Instant createdAt;
}
