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
@Table(name = "packaging_entry")
@Getter
@Setter
public class PackagingEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_id", nullable = false)
    private String transactionId;

    @ManyToOne
    @JoinColumn(name = "agency_id", nullable = false)
    private Agency agency;

    @Column(name = "packaging_type", nullable = false)
    private String packagingType;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Column(name = "id_number", nullable = false)
    private String idNumber;

    @Column
    private String detail;

    @Column(nullable = false)
    private int inflow;

    @Column(nullable = false)
    private int outflow;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
