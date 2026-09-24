package com.cafeoccidente.backend.purchases.future.entity;

import com.cafeoccidente.backend.purchases.shared.entity.Agency;
import com.cafeoccidente.backend.purchases.shared.entity.Fund;
import com.cafeoccidente.backend.purchases.shared.entity.ProductCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
@Getter
@Setter
public class FertiFuturoPurchase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "purchase_date", nullable = false)
    private LocalDate purchaseDate;

    @Column(name = "invoice_number", nullable = false, unique = true)
    private Integer invoiceNumber;

    @ManyToOne
    @JoinColumn(name = "agency_id", nullable = false)
    private Agency agency;

    /** RP o LF (Cuadro combinado37). */
    @ManyToOne
    @JoinColumn(name = "fund_id", nullable = false)
    private Fund fund;

    /** Especial (Cuadro combinado61: RN/NESS/CORR/TE - "NESS" resuelve a los mismos Cod_Prod que
     *  "NESPRESSO - FTUSA" en Seco/Otros, ver migración V25). */
    @Column(name = "special_type", nullable = false)
    private String specialType;

    @ManyToOne
    @JoinColumn(name = "product_code_id", nullable = false)
    private ProductCode productCode;

    @Column(name = "announcement_number", nullable = false)
    private String announcementNumber;

    @Column(name = "announcement_date", nullable = false)
    private LocalDate announcementDate;

    /** Compromiso de Compras a Futuro que esta liquidacion paga, si aplica (Form_FUTURE
     *  FERTIFUTURO.bas: Texto29_AfterUpdate descuenta esta entrega del Saldo_Kilos del compromiso -
     *  ver FertiFuturoPurchaseServiceImpl). Nullable: no toda liquidacion tiene que venir de un
     *  compromiso previo registrado en el sistema. */
    @ManyToOne
    @JoinColumn(name = "future_purchase_id")
    private FuturePurchase futurePurchase;

    @Column(name = "id_number", nullable = false)
    private String idNumber;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "grower_type", nullable = false)
    private String growerType;

    @Column(nullable = false)
    private String address;

    @Column(nullable = false)
    private Integer sacos;

    /** Kilos_Netos: a diferencia de Seco/Otros, es un INPUT directo aca (Sacos_AfterUpdate/
     *  Kilos_Brutos_AfterUpdate derivan Destare/Kilos_Brutos a partir de este, no al reves). */
    @Column(name = "net_kg", nullable = false, precision = 10, scale = 2)
    private BigDecimal netKg;

    @Column(name = "gross_kg", nullable = false, precision = 10, scale = 2)
    private BigDecimal grossKg;

    /** Destare = Kilos_Brutos - Kilos_Netos (Kilos_Brutos_AfterUpdate). */
    @Column(name = "tare_kg", nullable = false, precision = 10, scale = 2)
    private BigDecimal tareKg;

    @Column(name = "healthy_stored_weight", nullable = false, precision = 10, scale = 2)
    private BigDecimal healthyStoredWeight;

    /** PorcAlmSana = (W_AlmSana*100)/250 (W_AlmSana_AfterUpdate - 250 fijo, no sampleSize). */
    @Column(name = "healthy_percentage", nullable = false, precision = 6, scale = 2)
    private BigDecimal healthyPercentage;

    @Column(name = "defective_stored_weight", nullable = false, precision = 10, scale = 2)
    private BigDecimal defectiveStoredWeight;

    @Column(name = "defective_percentage", nullable = false, precision = 6, scale = 2)
    private BigDecimal defectivePercentage;

    /** Pr_AlmSana, congelado del anuncio al momento de la liquidación. */
    @Column(name = "healthy_unit_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal healthyUnitPrice;

    @Column(name = "defective_unit_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal defectiveUnitPrice;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal bonus;

    /** Castigo. */
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal penalty;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal costs;

    /** VrIncCalidad: tasa cruda del anuncio (Announcement.qualityIncrement), congelada aca. */
    @Column(name = "quality_increment_rate", nullable = false, precision = 15, scale = 2)
    private BigDecimal qualityIncrementRate;

    /** IncCalidad calculado: max(0, PorcAlmSana - 75) * VrIncCalidad (W_AlmSana_AfterUpdate). */
    @Column(name = "quality_increment_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal qualityIncrementAmount;

    @Column(name = "unit_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "gross_value", nullable = false, precision = 15, scale = 2)
    private BigDecimal grossValue;

    @Column(name = "inventory_value", nullable = false, precision = 15, scale = 2)
    private BigDecimal inventoryValue;

    @Column(name = "associate_contribution", nullable = false, precision = 15, scale = 2)
    private BigDecimal associateContribution;

    @Column(name = "cooperative_discount", nullable = false, precision = 15, scale = 2)
    private BigDecimal cooperativeDiscount;

    @Column(name = "withholding_exempt", nullable = false)
    private boolean withholdingExempt;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal withholding;

    @Column(name = "freight_discount", nullable = false, precision = 15, scale = 2)
    private BigDecimal freightDiscount;

    @Column(name = "other_discounts", nullable = false, precision = 15, scale = 2)
    private BigDecimal otherDiscounts;

    @Column(name = "net_to_pay", nullable = false, precision = 15, scale = 2)
    private BigDecimal netToPay;

    @Column(name = "payment_method", nullable = false)
    private String paymentMethod;

    @Column(name = "check_number")
    private String checkNumber;

    @Column(name = "created_by_user_id", nullable = false)
    private Long createdByUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
