package com.cafeoccidente.backend.purchases.othercoffee.entity;

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
 * Compra "Cafés Otros" (formulario "COMPRASESP" en Access, Form_COMPRASESP.bas). Mismo layout que
 * {@link com.cafeoccidente.backend.purchases.drycoffee.entity.DryCoffeePurchase}: ~95% la misma
 * cascada de Café Seco, reusando Fund (RP/LF) para la familia de producto en vez de un campo
 * propio - "Cuadro combinado37" en el VBA es el mismo control Fondo que ya existe en Seco/Verde/
 * Pasilla. La UNICA diferencia real de formula (confirmada linea por linea contra
 * Form_COMPRASESP.bas: Castigo_lostFocus) es que Vr_Kilo NO suma el ajuste por Pr_AlmDefec
 * (var4/var10 en el VBA se calcula pero nunca se usa) - ver OtherCoffeePurchaseCalculator.
 */
@Entity
@Table(name = "compras_cafes_otros")
@Getter
@Setter
public class OtherCoffeePurchase {

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

    /** RP o LF (Cuadro combinado37) - junto con specialType resuelve productCode. */
    @ManyToOne
    @JoinColumn(name = "fondo_id", nullable = false)
    private Fund fund;

    @Column(name = "especial", nullable = false)
    private String specialType;

    @ManyToOne
    @JoinColumn(name = "producto_id", nullable = false)
    private ProductCode productCode;

    @Column(name = "numero_anuncio", nullable = false)
    private String announcementNumber;

    @Column(name = "fecha_anuncio", nullable = false)
    private LocalDate announcementDate;

    @Column(name = "pr_base_cps", nullable = false, precision = 15, scale = 2)
    private BigDecimal basePriceLoad;

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

    @Column(name = "celular", nullable = false)
    private String cellphone;

    @Column(name = "sacos", nullable = false)
    private Integer bagsCount;

    @Column(name = "kilos_brutos", nullable = false, precision = 10, scale = 2)
    private BigDecimal grossKg;

    @Column(name = "destare", nullable = false, precision = 10, scale = 2)
    private BigDecimal tareKg;

    @Column(name = "kilos_netos", nullable = false, precision = 10, scale = 2)
    private BigDecimal netKg;

    @Column(name = "peso_tot_alm", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalStoredWeight;

    @Column(name = "porc_merma", nullable = false, precision = 6, scale = 2)
    private BigDecimal wastePercentage;

    @Column(name = "peso_tot_pasilla", nullable = false, precision = 10, scale = 2)
    private BigDecimal defectiveStoredWeight;

    @Column(name = "porc_kg_pasilla", nullable = false, precision = 6, scale = 2)
    private BigDecimal defectivePercentage;

    @Column(name = "peso_alm_sana", nullable = false, precision = 10, scale = 2)
    private BigDecimal healthyStoredWeight;

    @Column(name = "factor", nullable = false, precision = 6, scale = 2)
    private BigDecimal healthyPercentage;

    @Column(name = "pr_alm_sana", nullable = false, precision = 15, scale = 2)
    private BigDecimal healthyUnitPrice;

    /** Pr_AlmDefec: se guarda igual que en Seco (viene del anuncio) aunque NO participa en Vr_Kilo
     *  aca (ver clase). */
    @Column(name = "pr_alm_defec", nullable = false, precision = 15, scale = 2)
    private BigDecimal defectiveUnitPrice;

    @Column(name = "bonificacion", nullable = false, precision = 15, scale = 2)
    private BigDecimal bonus;

    @Column(name = "castigo", nullable = false, precision = 15, scale = 2)
    private BigDecimal penalty;

    @Column(name = "costos", nullable = false, precision = 15, scale = 2)
    private BigDecimal costs;

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
