package com.cafeoccidente.backend.purchases.shared.entity;

import jakarta.persistence.Column;
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
 * Caficultor (tabla "Asociados" en Access, ~27,328 registros - todavia no cargados, ver Flyway).
 * Referencia de estructura: docs/legacy-postgres-reference/PosgreSQL/03_asociados.sql.
 */
@Entity
@Table(name = "terceros")
@Getter
@Setter
public class Grower {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Cedula (cedula_cafetera en el dump). */
    @Column(name = "identificacion", nullable = false, unique = true)
    private String idNumber;

    @Column(name = "primer_nombre", nullable = false)
    private String firstName;

    @Column(name = "segundo_nombre")
    private String secondName;

    /** Nulo en Access para asociaciones/fundaciones (todo el nombre va en firstName) - V34. */
    @Column(name = "primer_apellido")
    private String lastName;

    @Column(name = "segundo_apellido")
    private String secondLastName;

    /** Nula en Access para algunas filas de Asociados - V34. */
    @ManyToOne
    @JoinColumn(name = "agencia_id")
    private Agency agency;

    @Column(name = "fecha_nacimiento")
    private LocalDate birthDate;

    @Column(name = "direccion", nullable = false)
    private String address;

    @Column(name = "telefono", nullable = false)
    private String phone;

    @Column(name = "fecha_afiliacion")
    private LocalDate affiliationDate;

    /** Tipo: S = asociado, C = no asociado, F = fallecido (mismo campo que DryCoffeePurchase.growerType). */
    @Column(name = "tipo", nullable = false)
    private String growerType;

    @Column(name = "activo", nullable = false)
    private boolean active = true;

    @Column(name = "fallecido", nullable = false)
    private boolean deceased = false;

    @Column(name = "retirado", nullable = false)
    private boolean withdrawn = false;

    /** "Conductor" en el VBA (Form_Conductores.bas) es este mismo Asociado, no una entidad aparte -
     *  ver migración V26. Ambos nullable: la mayoría de los caficultores no son conductores. */
    @Column(name = "empresa_transporte")
    private String transportCompany;

    @Column(name = "vehiculo")
    private String vehiclePlate;

    /** Ultima actualizacion de Emp. Transp./Vehiculo desde "Ingresar Conductores" (V29). */
    @Column(name = "actualizado_en")
    private Instant updatedAt;

    // Resto de columnas de Asociados (V34), tal cual en Access.

    /** Sexo: F / M / E (E = entidad: asociacion, empresa). */
    @Column(name = "sexo", length = 1)
    private String sex;

    /** Asociados.Asociacion: el vendedor es Asociacion o Fundacion (formulario Asociaciones1). */
    @Column(name = "asociacion")
    private Boolean isAssociation;

    @Column(name = "aceptado")
    private Boolean accepted;

    /** Asociados.Habil. */
    @Column(name = "habil")
    private Boolean eligible;

    @Column(name = "estado_civil")
    private String maritalStatus;

    @Column(name = "lugar_nacimiento")
    private String birthPlace;

    @Column(name = "cedula_cafetera")
    private String coffeeIdCard;

    @Column(name = "acta")
    private String actNumber;

    @Column(name = "observacion")
    private String observation;

    /** Asociados.Ciu. */
    @Column(name = "ciudad")
    private String cityCode;

    @Column(name = "codigo_postal")
    private String postalCode;

    @Column(name = "email")
    private String email;

    /** Asociados.NumRegistro: une con RegControl en IngresaVendedor. */
    @Column(name = "numero_registro")
    private Integer registryNumber;

    /** Asociados.Exportado: dato de Access que Consultar Asociados muestra (RF-28.3); los nuevos nacen en false. */
    @Column(name = "exportado")
    private Boolean exported;

    /** Asociados.Nuevo. */
    @Column(name = "nuevo")
    private Boolean isNew;

    /** Asociados.Pais (codigo). */
    @Column(name = "pais")
    private String country;
}
