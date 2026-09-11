package com.cafeoccidente.backend.purchases.drycoffee.entity;

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
 * Compra de cafe seco (formulario "Compras Cafe Seco" en Access). Migracion fiel del formulario
 * VBA original: los campos calculados (netKg, wastePercentage, defectivePercentage,
 * healthyPercentage, unitPrice, grossValue, inventoryValue, associateContribution,
 * cooperativeDiscount, withholding, netToPay) se recalculan siempre en el servidor
 * (DryCoffeePurchaseCalculator) - nunca se confia en el valor que mande el cliente.
 */
@Entity
@Getter
@Setter
public class DryCoffeePurchase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "purchase_date", nullable = false)
    private LocalDate purchaseDate;

    /** Factura: consecutivo dentro del rango autorizado en ControlRecord (resolutionFrom/resolutionTo). */
    @Column(name = "invoice_number", nullable = false, unique = true)
    private Integer invoiceNumber;

    @ManyToOne
    @JoinColumn(name = "agency_id", nullable = false)
    private Agency agency;

    @ManyToOne
    @JoinColumn(name = "fund_id", nullable = false)
    private Fund fund;

    /** "Especial" (Cuadro_combinado61) usado junto con fund para resolver productCode. */
    @Column(name = "special_type", nullable = false)
    private String specialType;

    @ManyToOne
    @JoinColumn(name = "product_code_id", nullable = false)
    private ProductCode productCode;

    @Column(name = "announcement_number", nullable = false)
    private String announcementNumber;

    @Column(name = "announcement_date", nullable = false)
    private LocalDate announcementDate;

    /** Pr_Base_PC = valor del anuncio (vrcps) - (Costos * ControlRecord.baseLoad). Ver DryCoffeePurchaseCalculator. */
    @Column(name = "base_price_load", nullable = false, precision = 15, scale = 2)
    private BigDecimal basePriceLoad;

    @Column(name = "id_number", nullable = false)
    private String idNumber;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    /** Tipo: S = asociado, C = no asociado, F = fallecido (bloquea la compra). */
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

    /** Asociacion (checkbox en el VBA): si esta marcado, el caficultor esta exento de Retefuente. */
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

    /** CHEQUE o EFECTIVO (Cuadro_combinado39). Sin reconciliacion multi-instrumento (fuera de alcance). */
    @Column(name = "payment_method", nullable = false)
    private String paymentMethod;

    @Column(name = "check_number")
    private String checkNumber;

    /** Auditoria: usuario autenticado que registro la compra. */
    @Column(name = "created_by_user_id", nullable = false)
    private Long createdByUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
