-- HU-03 (RF-02): permisos temporales por pantalla. La tabla de V1 solo tenia id, estaba vacia y
-- ningun codigo la usaba. El permiso dura hasta que el Administrador lo retire (RN-02.3): retirar
-- borra la fila, y el quien y el cuando quedan en historial_cambios.
DROP TABLE permisos;

CREATE TABLE permisos (
    id BIGSERIAL,
    usuario_id bigint NOT NULL,
    pantalla character varying(40) NOT NULL,
    concedido_por bigint NOT NULL,
    concedido_en timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT pk_permisos PRIMARY KEY (id),
    CONSTRAINT fk_permisos_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
    CONSTRAINT fk_permisos_concedido_por FOREIGN KEY (concedido_por) REFERENCES usuarios(id),
    CONSTRAINT uq_permisos_usuario_pantalla UNIQUE (usuario_id, pantalla),
    CONSTRAINT ck_permisos_pantalla CHECK (pantalla IN
        ('ANULAR_DOCUMENTO', 'ACCESO_TABLAS', 'ACTUALIZAR_ANUNCIOS', 'REGISTRO_CONTROL', 'HISTORIAL'))
);

-- Conceder y retirar permisos quedan en el Historial.
ALTER TABLE historial_cambios
    DROP CONSTRAINT ck_historial_cambios_entidad,
    DROP CONSTRAINT ck_historial_cambios_accion;
ALTER TABLE historial_cambios
    ADD CONSTRAINT ck_historial_cambios_entidad
        CHECK (entidad IN ('REGISTRO_CONTROL', 'ANUNCIO', 'USUARIO', 'AGENCIA', 'PERMISO')),
    ADD CONSTRAINT ck_historial_cambios_accion
        CHECK (accion IN ('CREAR', 'MODIFICAR', 'REASIGNAR', 'ACTIVAR', 'DESACTIVAR', 'PUBLICAR', 'CONCEDER', 'RETIRAR'));
