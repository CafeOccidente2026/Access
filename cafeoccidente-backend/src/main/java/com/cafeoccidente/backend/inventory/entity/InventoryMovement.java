package com.cafeoccidente.backend.inventory.entity;

import com.cafeoccidente.backend.purchases.shared.entity.Agency;
import com.cafeoccidente.backend.purchases.shared.entity.ProductCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Entrada de inventario (Access "INVENTARIO"/INVENTARIOTB.txt): una fila por CADA compra real
 * (Seco/Verde/Pasilla), creada automáticamente al guardarse la compra (ver
 * InventoryMovementService, enganchado desde cada *PurchaseServiceImpl.create()). remainingKg se
 * consume vía {@link RemissionLine} - ver esa clase para la regla de saldo (Form_EXITS.bas,
 * Cantidad_AfterUpdate).
 */
@Entity
@Table(name = "inventarios")
@Getter
@Setter
public class InventoryMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** De qué módulo de compra viene esta entrada (5 tablas de origen distintas). */
    @Column(name = "modulo_compra", nullable = false)
    @Enumerated(EnumType.STRING)
    private PurchaseModule purchaseModule;

    /** Id de la fila en la tabla del módulo de origen (DryCoffeePurchase, GreenCoffeePurchase o HuskPurchase). */
    @Column(name = "compra_id", nullable = false)
    private Long purchaseId;

    @ManyToOne
    @JoinColumn(name = "agencia_id", nullable = false)
    private Agency agency;

    @ManyToOne
    @JoinColumn(name = "producto_id", nullable = false)
    private ProductCode productCode;

    @Column(name = "especial", nullable = false)
    private String specialType;

    @Column(name = "numero_factura", nullable = false)
    private Integer invoiceNumber;

    @Column(name = "fecha_compra", nullable = false)
    private LocalDate purchaseDate;

    @Column(name = "sacos")
    private Integer sacos;

    @Column(name = "kilos_brutos", precision = 10, scale = 2)
    private BigDecimal grossKg;

    @Column(name = "kilos_netos", nullable = false, precision = 10, scale = 2)
    private BigDecimal netKg;

    /** Saldo disponible para despachar (Salidas!Saldo, ParaSalidas!saldokg) - arranca en netKg. */
    @Column(name = "saldo_kilos", nullable = false, precision = 10, scale = 2)
    private BigDecimal remainingKg;

    @Column(name = "factor", precision = 6, scale = 2)
    private BigDecimal healthyPercentage;

    @Column(name = "vr_inventario", nullable = false, precision = 15, scale = 2)
    private BigDecimal inventoryValue;

    @Column(name = "creado_en", nullable = false)
    private Instant createdAt;

    public enum PurchaseModule {
        DRY_COFFEE, GREEN_COFFEE, HUSK
    }
}
