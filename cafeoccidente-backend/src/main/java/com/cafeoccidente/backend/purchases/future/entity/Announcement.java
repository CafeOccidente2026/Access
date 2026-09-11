package com.cafeoccidente.backend.purchases.future.entity;

import com.cafeoccidente.backend.purchases.shared.entity.Agency;
import com.cafeoccidente.backend.purchases.shared.entity.Fund;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * Anuncio de precio vigente para una agencia/fondo (campos Anuncio/Fecha_Anuncio/Pr_Base_PC del
 * formulario de compras). No hay pantalla de administracion todavia (se carga por Flyway); una
 * futura pantalla ADMIN para crearlos queda pendiente (ver README).
 */
@Entity
@Getter
@Setter
public class Announcement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "announcement_number", nullable = false)
    private String announcementNumber;

    @Column(name = "announcement_date", nullable = false)
    private LocalDate announcementDate;

    @Column(name = "base_price_load", nullable = false, precision = 15, scale = 2)
    private BigDecimal basePriceLoad;

    /**
     * Pr_AlmDefec: precio de la almendra defectuosa vigente en este anuncio (igual que
     * basePriceLoad/Pr_AlmSana, lo trae el anuncio - no se captura a mano en el formulario de
     * compra). Usado en DryCoffeePurchaseCalculator.
     */
    @Column(name = "defective_unit_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal defectiveUnitPrice;

    /** Pr_AlmSana: precio de la almendra sana vigente (autocompleta "Pr Sustentación"). */
    @Column(name = "healthy_unit_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal healthyUnitPrice;

    /** Bonificacion vigente en el anuncio (autocompleta "Bonificación"). */
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal bonus;

    /** Costos vigentes en el anuncio (autocompleta "Costos"; alimenta Pr_Base_PC en el calculador). */
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal costs;

    @ManyToOne
    @JoinColumn(name = "agency_id", nullable = false)
    private Agency agency;

    @ManyToOne
    @JoinColumn(name = "fund_id", nullable = false)
    private Fund fund;

    @Column(nullable = false)
    private boolean active = true;
}
