package com.cafeoccidente.backend.purchases.future.entity;

import com.cafeoccidente.backend.purchases.shared.entity.Agency;
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
 * Compromiso de entrega futura de café (formulario "Compras a Futuro" en Access, Form_COMPRAS A
 * FUTURO.bas). NO es una compra con pago: no hay cascada de precio (Vr_Kilo/Vr_Bruto/Neto_a_Pagar)
 * ni numeración de factura en el VBA real - solo el registro del compromiso (cédula, Especial,
 * kilos anunciados, fecha de entrega) y una carta-manifiesto imprimible (ver
 * FuturePurchaseServiceImpl). El pago real de lo entregado se liquida después en FERTIFUTURO
 * (módulo aparte).
 */
@Entity
@Table(name = "compras_anunciadas")
@Getter
@Setter
public class FuturePurchase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "creado_en", nullable = false)
    private Instant createdAt;

    @ManyToOne
    @JoinColumn(name = "agencia_id", nullable = false)
    private Agency agency;

    @Column(name = "numero_anuncio", nullable = false)
    private String announcementNumber;

    @Column(name = "fecha_anuncio", nullable = false)
    private LocalDate announcementDate;

    @Column(name = "cedula", nullable = false)
    private String idNumber;

    @Column(name = "nombres", nullable = false)
    private String firstName;

    /** Copia de Asociados: nulo si el Asociado no tiene apellido (V34). */
    @Column(name = "apellidos")
    private String lastName;

    /** Tipo: S = asociado, C = no asociado, F = fallecido (bloquea el anuncio, Texto34_AfterUpdate). */
    @Column(name = "tipo", nullable = false)
    private String growerType;

    @Column(name = "direccion", nullable = false)
    private String address;

    /** Especial (Cuadro_combinado30). */
    @Column(name = "especial", nullable = false)
    private String specialType;

    /** Kilos_Anunciados. */
    @Column(name = "kilos", nullable = false, precision = 10, scale = 2)
    private BigDecimal announcedKg;

    /** Saldo_Kilos: arranca igual a announcedKg (Kilos_LostFocus: SaldoKilos = Kilos). */
    @Column(name = "saldo", nullable = false, precision = 10, scale = 2)
    private BigDecimal remainingKg;

    /** Fecha_Entrega (Texto34). */
    @Column(name = "fecha_entrega", nullable = false)
    private LocalDate deliveryDate;

    @Column(name = "finca")
    private String finca;

    @Column(name = "municipio")
    private String municipality;

    @Column(name = "vereda")
    private String vereda;

    /** Pr_AlmSana ("Pr Sustentación"), congelado del anuncio al momento del compromiso. */
    @Column(name = "pr_alm_sana", nullable = false, precision = 15, scale = 2)
    private BigDecimal healthyUnitPrice;

    @Column(name = "pr_alm_defec", nullable = false, precision = 15, scale = 2)
    private BigDecimal defectiveUnitPrice;

    @Column(name = "bonificacion", nullable = false, precision = 15, scale = 2)
    private BigDecimal bonus;

    @Column(name = "costos", nullable = false, precision = 15, scale = 2)
    private BigDecimal costs;

    /** VrIncCalidad ("Pr IncCalidad") - ver Announcement.qualityIncrement. */
    @Column(name = "pr_inc_calidad", nullable = false, precision = 15, scale = 2)
    private BigDecimal qualityIncrement;

    /** prbcps: Pr_Base_CPS crudo del anuncio (NO el Pr_Base_PC ya neto de Costos*BaseCarga que usan
     *  Seco/Verde/Otros - confirmado contra la macro "Asignar numero anuncio * PFuture Buys", que
     *  copia ÚltimoDePr_Base_CPS tal cual). */
    @Column(name = "pr_base_cps", nullable = false, precision = 15, scale = 2)
    private BigDecimal basePriceLoad;

    @Column(name = "creado_por_usuario_id", nullable = false)
    private Long createdByUserId;
}
