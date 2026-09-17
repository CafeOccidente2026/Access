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
 * Anuncio "maestro": precio crudo publicado por el admin (Pr_Base_CPS/Pr_AlmDefec/SobrePr_CPS),
 * compartido por TODAS las agencias con ControlRecord activo (ver docs/diseno-anuncios-compartidos.md).
 * Se crea desde "Actualizar Anuncio con Factor" (ADMIN); cada actualizacion de precio inserta un
 * anuncio nuevo, nunca edita uno existente. La numeracion propia de cada agencia vive en
 * {@link AgencyAnnouncementNumber}, no aqui.
 *
 * <p>announcementNumber/agency/healthyUnitPrice/bonus/costs quedan SIN USAR por el codigo nuevo
 * (Costos/Pr Sustentacion/Bonificacion ahora se calculan en el momento de la compra con el
 * ControlRecord vivo de la agencia que compra, ver AnnouncementServiceImpl) - se conservan solo para
 * no perder los anuncios historicos que ya los tenian poblados.
 */
@Entity
@Getter
@Setter
public class Announcement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** @deprecated sin usar desde el anuncio compartido - la numeracion vive en
     *  {@link AgencyAnnouncementNumber}, una por agencia. Se conserva sin usar solo para no perder
     *  los anuncios historicos que ya lo tenian poblado. */
    @Deprecated
    @Column(name = "announcement_number")
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

    /**
     * SobrePr_CPS: sobreprecio crudo digitado por el admin (Form_ANUNCIOS CORRF.bas). Junto con
     * basePriceLoad es el dato crudo del maestro; Bonificacion (SobrePr_CPS / BaseCarga) se deriva
     * de este valor con el BaseCarga de cada agencia al momento de la compra, no aqui.
     */
    @Column(name = "special_surcharge", precision = 15, scale = 2)
    private BigDecimal specialSurcharge;

    /** @deprecated sin usar desde el anuncio compartido - ver Javadoc de la clase. */
    @Deprecated
    @Column(name = "healthy_unit_price", precision = 15, scale = 2)
    private BigDecimal healthyUnitPrice;

    /** @deprecated sin usar desde el anuncio compartido - ver Javadoc de la clase. */
    @Deprecated
    @Column(precision = 15, scale = 2)
    private BigDecimal bonus;

    /** @deprecated sin usar desde el anuncio compartido - ver Javadoc de la clase. */
    @Deprecated
    @Column(precision = 15, scale = 2)
    private BigDecimal costs;

    /** @deprecated sin usar desde el anuncio compartido - ver Javadoc de la clase. */
    @Deprecated
    @ManyToOne
    @JoinColumn(name = "agency_id")
    private Agency agency;

    @ManyToOne
    @JoinColumn(name = "fund_id", nullable = false)
    private Fund fund;

    /** Especial (Cod_Prod): tipo especial de cafe al que aplica este anuncio (ESTANDAR si no aplica
     * ninguno especifico). Junto con agencia/fondo identifica el anuncio vigente a consultar. */
    @Column(name = "special_type", nullable = false)
    private String specialType;

    @Column(nullable = false)
    private boolean active = true;
}
