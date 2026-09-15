package com.cafeoccidente.backend.purchases.husk.entity;

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
 * Compra de pasilla (formulario "PASILLA" en Access). Migracion fiel de Form_PASILLA.bas: Especial
 * es fijo "PASILLA" y Fondo es fijo "RP" (ambos RowSource de un solo valor en PASILLA.txt), aunque
 * Cuadro_combinado61_AfterUpdate SI resuelve Cod_Prod via Especial+Fondo (igual que Cafe Seco) - la
 * rama LF existe en el VBA pero no es alcanzable desde el combo tal como esta configurado hoy.
 * A diferencia de Cafe Seco/VERDES, no hay formula Pr_Base_PC (Costos*BaseCarga): basePriceDryLoad
 * es el valor crudo del anuncio (Pr_Base_CPS/Texto103), sin restar nada.
 */
@Entity
@Getter
@Setter
public class HuskPurchase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "purchase_date", nullable = false)
    private LocalDate purchaseDate;

    /** Factura: "Para asignar # factura pasilla" = MAX(Factura de este modulo) + 1. */
    @Column(name = "invoice_number", nullable = false, unique = true)
    private Integer invoiceNumber;

    @ManyToOne
    @JoinColumn(name = "agency_id", nullable = false)
    private Agency agency;

    @ManyToOne
    @JoinColumn(name = "fund_id", nullable = false)
    private Fund fund;

    /** Especial: fijo "PASILLA" (RowSource="PASILLA" en Cuadro combinado61 de PASILLA.txt). */
    @Column(name = "special_type", nullable = false)
    private String specialType;

    @ManyToOne
    @JoinColumn(name = "product_code_id", nullable = false)
    private ProductCode productCode;

    @Column(name = "announcement_number", nullable = false)
    private String announcementNumber;

    @Column(name = "announcement_date", nullable = false)
    private LocalDate announcementDate;

    /** Pr_Base_CPS crudo del anuncio (Texto103, "Pr base carga perg seco") - sin formula de resta. */
    @Column(name = "base_price_dry_load", nullable = false, precision = 15, scale = 2)
    private BigDecimal basePriceDryLoad;

    @Column(name = "id_number", nullable = false)
    private String idNumber;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    /** Tipo: S = asociado, C = no asociado (Descuento_Fro_LostFocus). */
    @Column(name = "grower_type", nullable = false)
    private String growerType;

    @Column(nullable = false)
    private String address;

    @Column(nullable = false)
    private String cellphone;

    /** Pr_AlmSana vigente en el anuncio (etiqueta real "Pr Punto" en PASILLA.txt). Se usa en Vr_Kilo. */
    @Column(name = "point_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal pointPrice;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal costs;

    /** W_AlmSana: peso de almendra digitado a mano (W_AlmSana_AfterUpdate: se bloquea al confirmar). */
    @Column(name = "almond_weight", nullable = false, precision = 10, scale = 2)
    private BigDecimal almondWeight;

    /** PorcAlmSana = W_AlmSana * 100 / Muestra (ControlRecord.sampleSize). */
    @Column(name = "almond_percentage", nullable = false, precision = 6, scale = 2)
    private BigDecimal almondPercentage;

    @Column(name = "bags_count", nullable = false)
    private Integer bagsCount;

    @Column(name = "gross_kg", nullable = false, precision = 10, scale = 2)
    private BigDecimal grossKg;

    @Column(name = "tare_kg", nullable = false, precision = 10, scale = 2)
    private BigDecimal tareKg;

    /** Kilos_Netos = Kilos_Brutos - Destare (Destare_LostFocus, sin multiplicador). */
    @Column(name = "net_kg", nullable = false, precision = 10, scale = 2)
    private BigDecimal netKg;

    /** Vr_Kilo = (Pr_AlmSana * PorcAlmSana / BasePasilla) - Costos (Descuento_Fro_LostFocus). */
    @Column(name = "unit_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPrice;

    /** Vr_Bruto = Vr_Kilo * Kilos_Netos. */
    @Column(name = "gross_value", nullable = false, precision = 15, scale = 2)
    private BigDecimal grossValue;

    @Column(name = "inventory_value", nullable = false, precision = 15, scale = 2)
    private BigDecimal inventoryValue;

    @Column(name = "associate_contribution", nullable = false, precision = 15, scale = 2)
    private BigDecimal associateContribution;

    @Column(name = "cooperative_discount", nullable = false, precision = 15, scale = 2)
    private BigDecimal cooperativeDiscount;

    /** Asociacion (checkbox): si esta marcado, el caficultor esta exento de Retefuente. */
    @Column(name = "withholding_exempt", nullable = false)
    private boolean withholdingExempt;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal withholding;

    @Column(name = "shrinkage_discount", nullable = false, precision = 15, scale = 2)
    private BigDecimal shrinkageDiscount;

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
