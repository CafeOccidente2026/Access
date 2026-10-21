package com.cafeoccidente.backend.purchases.husk.entity;

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
 * Compra de pasilla (formulario "PASILLA" en Access). Migracion fiel de Form_PASILLA.bas: Especial
 * es fijo "PASILLA" y Fondo es fijo "RP" (ambos RowSource de un solo valor en PASILLA.txt), aunque
 * Cuadro_combinado61_AfterUpdate SI resuelve Cod_Prod via Especial+Fondo (igual que Cafe Seco) - la
 * rama LF existe en el VBA pero no es alcanzable desde el combo tal como esta configurado hoy.
 * A diferencia de Cafe Seco/VERDES, no hay formula Pr_Base_PC (Costos*BaseCarga): basePriceDryLoad
 * es el valor crudo del anuncio (Pr_Base_CPS/Texto103), sin restar nada.
 */
@Entity
@Table(name = "compras_pasilla")
@Getter
@Setter
public class HuskPurchase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fecha_compra", nullable = false)
    private LocalDate purchaseDate;

    /** Factura: "Para asignar # factura pasilla" = MAX(Factura de este modulo) + 1. */
    @Column(name = "numero_factura", nullable = false, unique = true)
    private Integer invoiceNumber;

    @ManyToOne
    @JoinColumn(name = "agencia_id", nullable = false)
    private Agency agency;

    @ManyToOne
    @JoinColumn(name = "fondo_id", nullable = false)
    private Fund fund;

    /** Especial: fijo "PASILLA" (RowSource="PASILLA" en Cuadro combinado61 de PASILLA.txt). */
    @Column(name = "especial", nullable = false)
    private String specialType;

    @ManyToOne
    @JoinColumn(name = "producto_id", nullable = false)
    private ProductCode productCode;

    @Column(name = "numero_anuncio", nullable = false)
    private String announcementNumber;

    @Column(name = "fecha_anuncio", nullable = false)
    private LocalDate announcementDate;

    /** Pr_Base_CPS crudo del anuncio (Texto103, "Pr base carga perg seco") - sin formula de resta. */
    @Column(name = "pr_base_cps", nullable = false, precision = 15, scale = 2)
    private BigDecimal basePriceDryLoad;

    @Column(name = "cedula", nullable = false)
    private String idNumber;

    @Column(name = "nombres", nullable = false)
    private String firstName;

    @Column(name = "apellidos", nullable = false)
    private String lastName;

    /** Tipo: S = asociado, C = no asociado (Descuento_Fro_LostFocus). */
    @Column(name = "tipo_vendedor", nullable = false)
    private String growerType;

    @Column(name = "direccion", nullable = false)
    private String address;

    @Column(name = "celular", nullable = false)
    private String cellphone;

    /** Pr_AlmSana vigente en el anuncio (etiqueta real "Pr Punto" en PASILLA.txt). Se usa en Vr_Kilo. */
    @Column(name = "pr_punto", nullable = false, precision = 15, scale = 2)
    private BigDecimal pointPrice;

    @Column(name = "costos", nullable = false, precision = 15, scale = 2)
    private BigDecimal costs;

    /** W_AlmSana: peso de almendra digitado a mano (W_AlmSana_AfterUpdate: se bloquea al confirmar). */
    @Column(name = "peso_almendra", nullable = false, precision = 10, scale = 2)
    private BigDecimal almondWeight;

    /** PorcAlmSana = W_AlmSana * 100 / Muestra (ControlRecord.sampleSize). */
    @Column(name = "porc_almendra", nullable = false, precision = 6, scale = 2)
    private BigDecimal almondPercentage;

    @Column(name = "sacos", nullable = false)
    private Integer bagsCount;

    @Column(name = "kilos_brutos", nullable = false, precision = 10, scale = 2)
    private BigDecimal grossKg;

    @Column(name = "destare", nullable = false, precision = 10, scale = 2)
    private BigDecimal tareKg;

    /** Kilos_Netos = Kilos_Brutos - Destare (Destare_LostFocus, sin multiplicador). */
    @Column(name = "kilos_netos", nullable = false, precision = 10, scale = 2)
    private BigDecimal netKg;

    /** Vr_Kilo = (Pr_AlmSana * PorcAlmSana / BasePasilla) - Costos (Descuento_Fro_LostFocus). */
    @Column(name = "vr_kilo", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPrice;

    /** Vr_Bruto = Vr_Kilo * Kilos_Netos. */
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
