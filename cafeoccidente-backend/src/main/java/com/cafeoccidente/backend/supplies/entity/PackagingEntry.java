package com.cafeoccidente.backend.supplies.entity;

import com.cafeoccidente.backend.purchases.shared.entity.Agency;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/** Tabla Empaque de Access (Empaque Suministros = Entradas, Prestamo Empaques = Salidas). */
@Entity
@Table(name = "empaques")
@Getter
@Setter
public class PackagingEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_transaccion", nullable = false)
    private String transactionId;

    @ManyToOne
    @JoinColumn(name = "agencia_id", nullable = false)
    private Agency agency;

    @Column(name = "tipo_empaque", nullable = false)
    private String packagingType;

    @Column(name = "fecha", nullable = false)
    private LocalDate entryDate;

    @Column(name = "cedula", nullable = false)
    private String idNumber;

    @Column(name = "detalle")
    private String detail;

    @Column(name = "entradas", nullable = false)
    private int inflow;

    @Column(name = "salidas", nullable = false)
    private int outflow;

    @Column(name = "creado_en", nullable = false)
    private Instant createdAt;
}
