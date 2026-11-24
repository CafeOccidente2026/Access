package com.cafeoccidente.backend.inventory.entity;

import com.cafeoccidente.backend.common.persistence.UpperCaseConverter;
import com.cafeoccidente.backend.purchases.shared.entity.Agency;
import com.cafeoccidente.backend.purchases.shared.entity.Fund;
import com.cafeoccidente.backend.purchases.shared.entity.Grower;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * Remisión / "Salidas" (Access "EXITS", Form_EXITS.bas): despacho de una o más entradas de
 * inventario hacia un destino, con conductor (= {@link Grower}, ver Grower.transportCompany/
 * vehiclePlate). Numeración correlativa simple por agencia (Access "Ultimo de remision", MAX+1).
 */
@Entity
@Table(name = "remisiones")
@Getter
@Setter
public class Remission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "numero_remision_anterior", nullable = false)
    private Integer remissionNumber;

    @ManyToOne
    @JoinColumn(name = "agencia_id", nullable = false)
    private Agency agency;

    @Column(name = "fecha_remision", nullable = false)
    private LocalDate remissionDate;

    @Convert(converter = UpperCaseConverter.class)
    @Column(name = "destino")
    private String destination;

    /** Conductor (cédula) - Form_Conductores.bas confirma que es el mismo Asociado/Grower. */
    @ManyToOne
    @JoinColumn(name = "conductor_tercero_id")
    private Grower conductor;

    @Column(name = "exportado", nullable = false)
    private boolean exported;

    @Column(name = "creado_por_usuario_id", nullable = false)
    private Long createdByUserId;

    @Column(name = "creado_en", nullable = false)
    private Instant createdAt;

    /** Fondo (LF/RP) de todas sus lineas (V30). */
    @ManyToOne
    @JoinColumn(name = "fondo_id")
    private Fund fund;

    /** Consecutivo por agencia + fondo; NULL en las remisiones anteriores a V30. */
    @Column(name = "consecutivo")
    private Integer sequenceNumber;

    /** Numero impreso: {prefijo}-{consecutivo} desde V31 (el mismo de su remesa); antes {prefijo}-{LF|RP}-{0000}
     *  y, en las de Access, su remission_number tal cual. */
    @Column(name = "numero_remision", nullable = false)
    private String displayNumber;

    /** VIGENTE o ANULADA (V31): la anulada no se borra. */
    @Column(name = "estado", nullable = false)
    private String status = VIGENTE;

    @Column(name = "anulada_por_usuario_id")
    private Long annulledByUserId;

    @Column(name = "anulada_en")
    private Instant annulledAt;

    /** Salida historica cargada desde Access por la migracion (V33): no se anula. */
    @Column(name = "de_access", nullable = false)
    private boolean fromAccess;

    public static final String VIGENTE = "VIGENTE";
    public static final String ANULADA = "ANULADA";
}
