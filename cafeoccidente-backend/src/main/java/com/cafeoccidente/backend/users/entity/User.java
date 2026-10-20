package com.cafeoccidente.backend.users.entity;

import com.cafeoccidente.backend.purchases.shared.entity.Agency;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "usuarios")
@Getter
@Setter
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario", nullable = false, unique = true)
    private String username;

    @Column(name = "contrasena_hash", nullable = false)
    private String passwordHash;

    @Column(name = "activo", nullable = false)
    private boolean active = true;

    @ManyToOne
    @JoinColumn(name = "rol_id", nullable = false)
    private Role role;

    @ManyToOne
    @JoinColumn(name = "agencia_id", nullable = false)
    private Agency agency;
}
