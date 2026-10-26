package com.cafeoccidente.backend.purchases.future.entity;

import com.cafeoccidente.backend.purchases.shared.entity.Fund;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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
 * {@link AgencyAnnouncementNumber}, no aqui. Costos, Pr Sustentacion y Bonificacion se calculan en el
 * momento de la compra con el ControlRecord vivo de la agencia que compra (AnnouncementServiceImpl); las
 * columnas viejas que los guardaban se borraron en V11 (D5).
 */
@Entity
@Table(name = "anuncios")
@Getter
@Setter
public class Announcement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fecha_anuncio", nullable = false)
    private LocalDate announcementDate;

    @Column(name = "pr_base_cps", nullable = false, precision = 15, scale = 2)
    private BigDecimal basePriceLoad;

    /**
     * Pr_AlmDefec: precio de la almendra defectuosa vigente en este anuncio (igual que
     * basePriceLoad/Pr_AlmSana, lo trae el anuncio - no se captura a mano en el formulario de
     * compra). Usado en DryCoffeePurchaseCalculator.
     */
    @Column(name = "pr_alm_defec", nullable = false, precision = 15, scale = 2)
    private BigDecimal defectiveUnitPrice;

    /**
     * SobrePr_CPS: sobreprecio crudo digitado por el admin (Form_ANUNCIOS CORRF.bas). Junto con
     * basePriceLoad es el dato crudo del maestro; Bonificacion (SobrePr_CPS / BaseCarga) se deriva
     * de este valor con el BaseCarga de cada agencia al momento de la compra, no aqui.
     */
    @Column(name = "sobrepr_carga_esp", precision = 15, scale = 2)
    private BigDecimal specialSurcharge;

    /**
     * Pr_AlmSana ("Pr Punto" en Form_ANUNCIOS PASILLA.bas): a diferencia de basePriceLoad en
     * CORRF, este formulario nunca tuvo formula que lo derivara de basePriceLoad/BaseCarga - el
     * admin lo digita directo en "Actualizar Anuncio Pasilla". Solo poblado cuando specialType =
     * PASILLA; para los demas se sigue derivando en toResponse().
     */
    @Column(name = "pr_punto", precision = 15, scale = 2)
    private BigDecimal pointPrice;

    /**
     * VrIncCalidad ("Pr IncCalidad"): columna real de ANUNCIOS en el legado (confirmada en
     * anuncios_migrar.csv), propagada a Compras a Futuro/FERTIFUTURO via las macros "Asignar
     * numero anuncio * PFuture Buys" y consumida en la cascada de FERTIFUTURO (var1 = var5 +
     * IncCalidad + Bonificacion). No se encontro control para editarla en "ANUNCIOS CORRF" (la
     * pantalla real de publicacion) - se expone igual en AnnouncementRequest por ser un dato real
     * del maestro. Nullable: los anuncios existentes no lo tienen poblado.
     */
    @Column(name = "pr_inc_calidad", precision = 15, scale = 2)
    private BigDecimal qualityIncrement;

    @ManyToOne
    @JoinColumn(name = "fondo_id", nullable = false)
    private Fund fund;

    /** Especial (Cod_Prod): tipo especial de cafe al que aplica este anuncio (ESTANDAR si no aplica
     * ninguno especifico). Junto con el fondo identifica el anuncio vigente a consultar. */
    @Column(name = "especial", nullable = false)
    private String specialType;

    @Column(name = "activo", nullable = false)
    private boolean active = true;
}
