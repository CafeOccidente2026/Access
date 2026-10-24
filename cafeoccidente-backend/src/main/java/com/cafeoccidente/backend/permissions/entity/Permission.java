package com.cafeoccidente.backend.permissions.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

/** Permiso vigente de un usuario a una pantalla (RF-02). Dura hasta que el Administrador lo retire. */
@Entity
@Table(name = "permisos")
@Getter
@Setter
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "pantalla", nullable = false)
    private Screen screen;

    @Column(name = "concedido_por", nullable = false)
    private Long grantedBy;

    @Column(name = "concedido_en", nullable = false, updatable = false)
    private OffsetDateTime grantedAt;
}
