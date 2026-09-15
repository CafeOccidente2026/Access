package com.cafeoccidente.backend.purchases.greencoffee.entity;

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
 * Compra de cafe verde (formulario "VERDES" en Access). Migracion fiel de Form_VERDES.bas:
 * Especial es fijo "CV" (RowSource="CV", sin Cuadro_combinado61_AfterUpdate) y Fondo es fijo "RP"
 * (DefaultValue, sin combo real) - a diferencia de Cafe Seco, Cod_Prod no depende de Fondo.
 * bonus/defectiveUnitPrice se guardan como snapshot del anuncio (se muestran) pero no participan
 * en ninguna formula (a diferencia de Cafe Seco) - no hay evidencia VBA de que Bonificacion o
 * Pr_AlmDefec se usen en un calculo en Form_VERDES.bas.
 */
@Entity
@Getter
@Setter
public class GreenCoffeePurchase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "purchase_date", nullable = false)
    private LocalDate purchaseDate;

    /** Factura: "Para asignar # factura verdes" = MAX(Factura de este modulo) + 1. */
    @Column(name = "invoice_number", nullable = false, unique = true)
    private Integer invoiceNumber;

    @ManyToOne
    @JoinColumn(name = "agency_id", nullable = false)
    private Agency agency;

    @ManyToOne
    @JoinColumn(name = "fund_id", nullable = false)
    private Fund fund;

    /** Especial: fijo "CV" (RowSource="CV" en Cuadro combinado61 de VERDES.txt). */
    @Column(name = "special_type", nullable = false)
    private String specialType;

    @ManyToOne
    @JoinColumn(name = "product_code_id", nullable = false)
    private ProductCode productCode;

    @Column(name = "announcement_number", nullable = false)
    private String announcementNumber;

    @Column(name = "announcement_date", nullable = false)
    private LocalDate announcementDate;

    /** Pr_Base_PC = Texto91 (Pr_Base_CPS del anuncio) - (Costos * BaseCarga). Cedula_AfterUpdate. */
    @Column(name = "base_price_load", nullable = false, precision = 15, scale = 2)
    private BigDecimal basePriceLoad;

    @Column(name = "id_number", nullable = false)
    private String idNumber;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    /** Tipo: S = asociado, C = no asociado (Castigo_lostFocus). */
    @Column(name = "grower_type", nullable = false)
    private String growerType;

    @Column(nullable = false)
    private String address;

    @Column(nullable = false)
    private String cellphone;

    @Column(name = "bags_count", nullable = false)
    private Integer bagsCount;

    @Column(name = "gross_kg", nullable = false, precision = 10, scale = 2)
    private BigDecimal grossKg;

    @Column(name = "tare_kg", nullable = false, precision = 10, scale = 2)
    private BigDecimal tareKg;

    /** Kilos_Verdes = Kilos_Brutos - Destare (Destare_LostFocus). */
    @Column(name = "green_kg", nullable = false, precision = 10, scale = 2)
    private BigDecimal greenKg;

    /** Kilos_Netos = Kilos_Verdes * PorcVerde / 100 (Destare_LostFocus). */
    @Column(name = "net_kg", nullable = false, precision = 10, scale = 2)
    private BigDecimal netKg;

    /** Pr_AlmSana vigente en el anuncio (snapshot, se muestra como "Pr Alm Sana"). */
    @Column(name = "healthy_unit_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal healthyUnitPrice;

    /** Pr_AlmDefec: snapshot del anuncio, no participa en ninguna formula de VERDES. */
    @Column(name = "defective_unit_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal defectiveUnitPrice;

    /** Bonificacion: snapshot del anuncio, no participa en ninguna formula de VERDES. */
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal bonus;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal costs;

    /** Castigo: capturado y mostrado, dispara la cascada (Castigo_lostFocus) pero no se resta en
     *  ninguna formula de VERDES (a diferencia de Cafe Seco). */
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal penalty;

    /** Vr_Kilo_Comp: precio de compra digitado a mano (Vr_Kilo_Comp_AfterUpdate: Vr_Kilo = Vr_Kilo_Comp). */
    @Column(name = "comp_kg_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal compKgPrice;

    @Column(name = "unit_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPrice;

    /** Vr_Bruto = Vr_Kilo_Comp * Kilos_Verdes (no Kilos_Netos - Castigo_lostFocus). */
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
