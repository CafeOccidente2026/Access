package com.cafeoccidente.backend.inventory.entity;

import com.cafeoccidente.backend.purchases.shared.entity.Agency;
import com.cafeoccidente.backend.purchases.shared.entity.Fund;
import com.cafeoccidente.backend.purchases.shared.entity.Grower;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
@Getter
@Setter
public class Remission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "remission_number", nullable = false)
    private Integer remissionNumber;

    @ManyToOne
    @JoinColumn(name = "agency_id", nullable = false)
    private Agency agency;

    @Column(name = "remission_date", nullable = false)
    private LocalDate remissionDate;

    @Column
    private String destination;

    /** Conductor (cédula) - Form_Conductores.bas confirma que es el mismo Asociado/Grower. */
    @ManyToOne
    @JoinColumn(name = "conductor_id")
    private Grower conductor;

    @Column(nullable = false)
    private boolean exported;

    @Column(name = "created_by_user_id", nullable = false)
    private Long createdByUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Fondo (LF/RP) de todas sus lineas (V30). */
    @ManyToOne
    @JoinColumn(name = "fund_id")
    private Fund fund;

    /** Consecutivo por agencia + fondo; NULL en las remisiones anteriores a V30. */
    @Column(name = "sequence_number")
    private Integer sequenceNumber;

    /** Numero impreso: {prefijo}-{LF|RP}-{0000}; en las viejas, su remission_number tal cual. */
    @Column(name = "display_number", nullable = false)
    private String displayNumber;
}
