-- Historial de cambios (pedido del area de compras, 2026-10-22; solo lo ve el Administrador):
-- quien cambio que y cuando, con los campos que cambiaron (antes y despues). Nunca guarda contrasenas.
CREATE TABLE historial_cambios (
    id BIGSERIAL,
    fecha timestamp with time zone NOT NULL DEFAULT now(),
    usuario_id bigint NOT NULL,
    entidad character varying(40) NOT NULL,
    entidad_id bigint NOT NULL,
    accion character varying(20) NOT NULL,
    agencia_id bigint,
    antes jsonb,
    despues jsonb,
    CONSTRAINT pk_historial_cambios PRIMARY KEY (id),
    CONSTRAINT fk_historial_cambios_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
    CONSTRAINT fk_historial_cambios_agencia FOREIGN KEY (agencia_id) REFERENCES agencias(id),
    CONSTRAINT ck_historial_cambios_entidad CHECK (entidad IN ('REGISTRO_CONTROL', 'ANUNCIO', 'USUARIO', 'AGENCIA')),
    CONSTRAINT ck_historial_cambios_accion
        CHECK (accion IN ('CREAR', 'MODIFICAR', 'REASIGNAR', 'ACTIVAR', 'DESACTIVAR', 'PUBLICAR'))
);
CREATE INDEX ix_historial_cambios_fecha ON historial_cambios (fecha DESC);
