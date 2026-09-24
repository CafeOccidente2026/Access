package com.cafeoccidente.backend.purchases.future.entity;

import com.cafeoccidente.backend.purchases.shared.entity.Agency;
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
 * Compromiso de entrega futura de café (formulario "Compras a Futuro" en Access, Form_COMPRAS A
 * FUTURO.bas). NO es una compra con pago: no hay cascada de precio (Vr_Kilo/Vr_Bruto/Neto_a_Pagar)
 * ni numeración de factura en el VBA real - solo el registro del compromiso (cédula, Especial,
 * kilos anunciados, fecha de entrega) y una carta-manifiesto imprimible (ver
 * FuturePurchaseServiceImpl). El pago real de lo entregado se liquida después en FERTIFUTURO
 * (módulo aparte).
 */
@Entity
@Getter
@Setter
public class FuturePurchase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @ManyToOne
    @JoinColumn(name = "agency_id", nullable = false)
    private Agency agency;

    @Column(name = "announcement_number", nullable = false)
    private String announcementNumber;

    @Column(name = "announcement_date", nullable = false)
    private LocalDate announcementDate;

    @Column(name = "id_number", nullable = false)
    private String idNumber;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    /** Tipo: S = asociado, C = no asociado, F = fallecido (bloquea el anuncio, Texto34_AfterUpdate). */
    @Column(name = "grower_type", nullable = false)
    private String growerType;

    @Column(nullable = false)
    private String address;

    /** Especial (Cuadro_combinado30). */
    @Column(name = "special_type", nullable = false)
    private String specialType;

    /** Kilos_Anunciados. */
    @Column(name = "announced_kg", nullable = false, precision = 10, scale = 2)
    private BigDecimal announcedKg;

    /** Saldo_Kilos: arranca igual a announcedKg (Kilos_LostFocus: SaldoKilos = Kilos). */
    @Column(name = "remaining_kg", nullable = false, precision = 10, scale = 2)
    private BigDecimal remainingKg;

    /** Fecha_Entrega (Texto34). */
    @Column(name = "delivery_date", nullable = false)
    private LocalDate deliveryDate;

    @Column
    private String finca;

    @Column
    private String municipality;

    @Column
    private String vereda;

    /** Pr_AlmSana ("Pr Sustentación"), congelado del anuncio al momento del compromiso. */
    @Column(name = "healthy_unit_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal healthyUnitPrice;

    @Column(name = "defective_unit_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal defectiveUnitPrice;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal bonus;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal costs;

    /** VrIncCalidad ("Pr IncCalidad") - ver Announcement.qualityIncrement. */
    @Column(name = "quality_increment", nullable = false, precision = 15, scale = 2)
    private BigDecimal qualityIncrement;

    /** prbcps: Pr_Base_CPS crudo del anuncio (NO el Pr_Base_PC ya neto de Costos*BaseCarga que usan
     *  Seco/Verde/Otros - confirmado contra la macro "Asignar numero anuncio * PFuture Buys", que
     *  copia ÚltimoDePr_Base_CPS tal cual). */
    @Column(name = "base_price_load", nullable = false, precision = 15, scale = 2)
    private BigDecimal basePriceLoad;

    @Column(name = "created_by_user_id", nullable = false)
    private Long createdByUserId;
}
