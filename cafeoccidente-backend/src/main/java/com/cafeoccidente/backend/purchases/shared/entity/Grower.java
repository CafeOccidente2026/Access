package com.cafeoccidente.backend.purchases.shared.entity;

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

    /** Nulo en Access para asociaciones/fundaciones (todo el nombre va en firstName) - V34. */
    @Column(name = "last_name")
    private String lastName;

    @Column(name = "second_last_name")
    private String secondLastName;

    /** Nula en Access para algunas filas de Asociados - V34. */
    @ManyToOne
    @JoinColumn(name = "agency_id")
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

    /** "Conductor" en el VBA (Form_Conductores.bas) es este mismo Asociado, no una entidad aparte -
     *  ver migración V26. Ambos nullable: la mayoría de los caficultores no son conductores. */
    @Column(name = "transport_company")
    private String transportCompany;

    @Column(name = "vehicle_plate")
    private String vehiclePlate;

    /** Ultima actualizacion de Emp. Transp./Vehiculo desde "Ingresar Conductores" (V29). */
    @Column(name = "updated_at")
    private Instant updatedAt;

    // Resto de columnas de Asociados (V34), tal cual en Access.

    /** Sexo: F / M / E (E = entidad: asociacion, empresa). */
    @Column(length = 1)
    private String sex;

    /** Asociados.Asociacion: el vendedor es Asociacion o Fundacion (formulario Asociaciones1). */
    @Column(name = "is_association")
    private Boolean isAssociation;

    @Column(name = "accepted")
    private Boolean accepted;

    /** Asociados.Habil. */
    @Column(name = "eligible")
    private Boolean eligible;

    @Column(name = "marital_status")
    private String maritalStatus;

    @Column(name = "birth_place")
    private String birthPlace;

    @Column(name = "coffee_id_card")
    private String coffeeIdCard;

    @Column(name = "act_number")
    private String actNumber;

    private String observation;

    /** Asociados.Ciu. */
    @Column(name = "city_code")
    private String cityCode;

    @Column(name = "postal_code")
    private String postalCode;

    private String email;

    /** Asociados.NumRegistro: une con RegControl en IngresaVendedor. */
    @Column(name = "registry_number")
    private Integer registryNumber;

    /** Asociados.Exportado: "Exportar Informacion" exporta los false y los marca. */
    private Boolean exported;

    /** Asociados.Nuevo. */
    @Column(name = "is_new")
    private Boolean isNew;

    /** Asociados.Pais (codigo). */
    private String country;
}
