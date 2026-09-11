package com.cafeoccidente.backend.purchases.shared.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * Caficultor (tabla "Asociados" en Access, ~27,328 registros - todavia no cargados, ver Flyway).
 * Referencia de estructura: docs/legacy-postgres-reference/PosgreSQL/03_asociados.sql.
 */
@Entity
@Getter
@Setter
public class Grower {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Cedula (cedula_cafetera en el dump). */
    @Column(name = "id_number", nullable = false, unique = true)
    private String idNumber;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "second_name")
    private String secondName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "second_last_name")
    private String secondLastName;

    @ManyToOne
    @JoinColumn(name = "agency_id", nullable = false)
    private Agency agency;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(nullable = false)
    private String address;

    @Column(nullable = false)
    private String phone;

    @Column(name = "affiliation_date")
    private LocalDate affiliationDate;

    /** Tipo: S = asociado, C = no asociado, F = fallecido (mismo campo que DryCoffeePurchase.growerType). */
    @Column(name = "grower_type", nullable = false)
    private String growerType;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private boolean deceased = false;

    @Column(nullable = false)
    private boolean withdrawn = false;
}
