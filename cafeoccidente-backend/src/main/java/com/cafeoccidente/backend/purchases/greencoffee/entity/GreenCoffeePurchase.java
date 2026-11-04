package com.cafeoccidente.backend.purchases.greencoffee.entity;

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
 * Compra de cafe verde (formulario "VERDES" en Access). Migracion fiel de Form_VERDES.bas:
 * Especial es fijo "CV" (RowSource="CV", sin Cuadro_combinado61_AfterUpdate) y Fondo es fijo "RP"
 * (DefaultValue, sin combo real) - a diferencia de Cafe Seco, Cod_Prod no depende de Fondo.
 * bonus/defectiveUnitPrice se guardan como snapshot del anuncio (se muestran) pero no participan
 * en ninguna formula (a diferencia de Cafe Seco) - no hay evidencia VBA de que Bonificacion o
 * Pr_AlmDefec se usen en un calculo en Form_VERDES.bas.
 */
@Entity
@Table(name = "compras_cafe_verde")
@Getter
@Setter
public class GreenCoffeePurchase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fecha_compra", nullable = false)
    private LocalDate purchaseDate;

    /** Factura: "Para asignar # factura verdes" = MAX(Factura de este modulo) + 1. */
    @Column(name = "numero_factura", nullable = false, unique = true)
    private Integer invoiceNumber;

    @ManyToOne
    @JoinColumn(name = "agencia_id", nullable = false)
    private Agency agency;

    @ManyToOne
    @JoinColumn(name = "fondo_id", nullable = false)
    private Fund fund;

    /** Especial: fijo "CV" (RowSource="CV" en Cuadro combinado61 de VERDES.txt). */
    @Column(name = "especial", nullable = false)
    private String specialType;

    @ManyToOne
    @JoinColumn(name = "producto_id", nullable = false)
    private ProductCode productCode;

    @Column(name = "numero_anuncio", nullable = false)
    private String announcementNumber;

    @Column(name = "fecha_anuncio", nullable = false)
    private LocalDate announcementDate;

    /** Pr_Base_PC = Texto91 (Pr_Base_CPS del anuncio) - (Costos * BaseCarga). Cedula_AfterUpdate. */
    @Column(name = "pr_base_cps", nullable = false, precision = 15, scale = 2)
    private BigDecimal basePriceLoad;

    @Column(name = "cedula", nullable = false)
    private String idNumber;

    @Column(name = "nombres", nullable = false)
    private String firstName;

    @Column(name = "apellidos", nullable = false)
    private String lastName;

    /** Tipo: S = asociado, C = no asociado (Castigo_lostFocus). */
    @Column(name = "tipo_vendedor", nullable = false)
    private String growerType;

    @Column(name = "direccion", nullable = false)
    private String address;

    @Column(name = "celular", nullable = false)
    private String cellphone;

    @Column(name = "sacos", nullable = false)
    private Integer bagsCount;

    @Column(name = "kilos_brutos", nullable = false, precision = 10, scale = 2)
    private BigDecimal grossKg;

    @Column(name = "destare", nullable = false, precision = 10, scale = 2)
    private BigDecimal tareKg;

    /** Kilos_Verdes = Kilos_Brutos - Destare (Destare_LostFocus). */
    @Column(name = "kilos_verdes", nullable = false, precision = 10, scale = 2)
    private BigDecimal greenKg;

    /** Kilos_Netos = Kilos_Verdes * PorcVerde / 100 (Destare_LostFocus). */
    @Column(name = "kilos_netos", nullable = false, precision = 10, scale = 2)
    private BigDecimal netKg;

    /** Muestra fija de verde del Registro de Control al momento de la compra (HU-23 CA-2, V15). */
    @Column(name = "peso_tot_alm", precision = 10, scale = 2)
    private BigDecimal totalStoredWeight;

    @Column(name = "porc_merma", precision = 10, scale = 2)
    private BigDecimal wastePercentage;

    @Column(name = "peso_tot_pasilla", precision = 10, scale = 2)
    private BigDecimal huskWeight;

    @Column(name = "porc_kg_pasilla", precision = 10, scale = 2)
    private BigDecimal huskPercentage;

    @Column(name = "peso_alm_sana", precision = 10, scale = 2)
    private BigDecimal healthyStoredWeight;

    @Column(name = "factor", precision = 10, scale = 2)
    private BigDecimal factor;

    /** Pr_AlmSana vigente en el anuncio (snapshot, se muestra como "Pr Alm Sana"). */
    @Column(name = "pr_alm_sana", nullable = false, precision = 15, scale = 2)
    private BigDecimal healthyUnitPrice;

    /** Pr_AlmDefec: snapshot del anuncio, no participa en ninguna formula de VERDES. */
    @Column(name = "pr_alm_defec", nullable = false, precision = 15, scale = 2)
    private BigDecimal defectiveUnitPrice;

    /** Bonificacion: snapshot del anuncio, no participa en ninguna formula de VERDES. */
    @Column(name = "bonificacion", nullable = false, precision = 15, scale = 2)
    private BigDecimal bonus;

    @Column(name = "costos", nullable = false, precision = 15, scale = 2)
    private BigDecimal costs;

    /** Castigo: capturado y mostrado, dispara la cascada (Castigo_lostFocus) pero no se resta en
     *  ninguna formula de VERDES (a diferencia de Cafe Seco). */
    @Column(name = "castigo", nullable = false, precision = 15, scale = 2)
    private BigDecimal penalty;

    /** Vr_Kilo_Comp: precio de compra digitado a mano (Vr_Kilo_Comp_AfterUpdate: Vr_Kilo = Vr_Kilo_Comp). */
    @Column(name = "vr_kilo_comp", nullable = false, precision = 15, scale = 2)
    private BigDecimal compKgPrice;

    @Column(name = "vr_kilo", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPrice;

    /** Vr_Bruto = Vr_Kilo_Comp * Kilos_Verdes (no Kilos_Netos - Castigo_lostFocus). */
    @Column(name = "vr_bruto", nullable = false, precision = 15, scale = 2)
    private BigDecimal grossValue;

    @Column(name = "vr_inventario", nullable = false, precision = 15, scale = 2)
    private BigDecimal inventoryValue;

    @Column(name = "aporte_asociado", nullable = false, precision = 15, scale = 2)
    private BigDecimal associateContribution;

    @Column(name = "descuento_cooperativo", nullable = false, precision = 15, scale = 2)
    private BigDecimal cooperativeDiscount;

    /** Asociacion (checkbox): si esta marcado, el caficultor esta exento de Retefuente. */
    @Column(name = "exento_retencion", nullable = false)
    private boolean withholdingExempt;

    @Column(name = "retefuente", nullable = false, precision = 15, scale = 2)
    private BigDecimal withholding;

    @Column(name = "descuento_financiero", nullable = false, precision = 15, scale = 2)
    private BigDecimal shrinkageDiscount;

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
