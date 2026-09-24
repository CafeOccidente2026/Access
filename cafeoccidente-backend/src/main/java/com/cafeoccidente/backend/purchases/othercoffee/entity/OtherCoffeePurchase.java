package com.cafeoccidente.backend.purchases.othercoffee.entity;

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
 * Compra "Cafés Otros" (formulario "COMPRASESP" en Access, Form_COMPRASESP.bas). Mismo layout que
 * {@link com.cafeoccidente.backend.purchases.drycoffee.entity.DryCoffeePurchase}: ~95% la misma
 * cascada de Café Seco, reusando Fund (RP/LF) para la familia de producto en vez de un campo
 * propio - "Cuadro combinado37" en el VBA es el mismo control Fondo que ya existe en Seco/Verde/
 * Pasilla. La UNICA diferencia real de formula (confirmada linea por linea contra
 * Form_COMPRASESP.bas: Castigo_lostFocus) es que Vr_Kilo NO suma el ajuste por Pr_AlmDefec
 * (var4/var10 en el VBA se calcula pero nunca se usa) - ver OtherCoffeePurchaseCalculator.
 */
@Entity
@Getter
@Setter
public class OtherCoffeePurchase {

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

    /** RP o LF (Cuadro combinado37) - junto con specialType resuelve productCode. */
    @ManyToOne
    @JoinColumn(name = "fund_id", nullable = false)
    private Fund fund;

    @Column(name = "special_type", nullable = false)
    private String specialType;

    @ManyToOne
    @JoinColumn(name = "product_code_id", nullable = false)
    private ProductCode productCode;

    @Column(name = "announcement_number", nullable = false)
    private String announcementNumber;

    @Column(name = "announcement_date", nullable = false)
    private LocalDate announcementDate;

    @Column(name = "base_price_load", nullable = false, precision = 15, scale = 2)
    private BigDecimal basePriceLoad;

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
    private String cellphone;

    @Column(name = "bags_count", nullable = false)
    private Integer bagsCount;

    @Column(name = "gross_kg", nullable = false, precision = 10, scale = 2)
    private BigDecimal grossKg;

    @Column(name = "tare_kg", nullable = false, precision = 10, scale = 2)
    private BigDecimal tareKg;

    @Column(name = "net_kg", nullable = false, precision = 10, scale = 2)
    private BigDecimal netKg;

    @Column(name = "total_stored_weight", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalStoredWeight;

    @Column(name = "waste_percentage", nullable = false, precision = 6, scale = 2)
    private BigDecimal wastePercentage;

    @Column(name = "defective_stored_weight", nullable = false, precision = 10, scale = 2)
    private BigDecimal defectiveStoredWeight;

    @Column(name = "defective_percentage", nullable = false, precision = 6, scale = 2)
    private BigDecimal defectivePercentage;

    @Column(name = "healthy_stored_weight", nullable = false, precision = 10, scale = 2)
    private BigDecimal healthyStoredWeight;

    @Column(name = "healthy_percentage", nullable = false, precision = 6, scale = 2)
    private BigDecimal healthyPercentage;

    @Column(name = "healthy_unit_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal healthyUnitPrice;

    /** Pr_AlmDefec: se guarda igual que en Seco (viene del anuncio) aunque NO participa en Vr_Kilo
     *  aca (ver clase). */
    @Column(name = "defective_unit_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal defectiveUnitPrice;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal bonus;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal penalty;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal costs;

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
