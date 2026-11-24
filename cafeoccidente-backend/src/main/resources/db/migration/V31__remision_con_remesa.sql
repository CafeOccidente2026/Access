-- Demo del 23-nov (respuestas del area): al guardar una remision se genera su remesa con el mismo numero, y una
-- remision se puede anular.

-- 1) Un solo consecutivo por agencia para remision y remesa (PREFIJO-consecutivo, ej. SDTA-969). Sigue desde el
--    mayor ya usado: el consecutivo por fondo de antes, el numero de remision de Access y el de las remesas.
CREATE TABLE consecutivos_remision_agencia (
    agencia_id         bigint  NOT NULL,
    ultimo_consecutivo integer NOT NULL,
    CONSTRAINT pk_consecutivos_remision_agencia PRIMARY KEY (agencia_id),
    CONSTRAINT fk_consecutivos_remision_agencia_agencia FOREIGN KEY (agencia_id) REFERENCES agencias (id)
);

INSERT INTO consecutivos_remision_agencia (agencia_id, ultimo_consecutivo)
SELECT agencia_id, MAX(ultimo) FROM (
    SELECT agencia_id, ultimo_consecutivo AS ultimo FROM consecutivos_remision
    UNION ALL SELECT agencia_id, numero_remision_anterior FROM remisiones
    UNION ALL SELECT agencia_id, consecutivo FROM remisiones WHERE consecutivo IS NOT NULL
    UNION ALL SELECT agencia_id, consecutivo FROM remesas WHERE consecutivo IS NOT NULL
) usados
GROUP BY agencia_id;

DROP TABLE consecutivos_remision;

-- 2) La remision anulada no se borra: queda ANULADA, con quien y cuando.
ALTER TABLE remisiones
    ADD COLUMN estado varchar(10) NOT NULL DEFAULT 'VIGENTE',
    ADD COLUMN anulada_por_usuario_id bigint,
    ADD COLUMN anulada_en timestamptz,
    ADD CONSTRAINT ck_remisiones_estado CHECK (estado IN ('VIGENTE', 'ANULADA')),
    ADD CONSTRAINT fk_remisiones_anulada_por_usuario FOREIGN KEY (anulada_por_usuario_id) REFERENCES usuarios (id);

-- 3) La remesa que se genero con cada remision (las de antes, de Genera Remesa o de Access, quedan sin remision).
ALTER TABLE remesas
    ADD COLUMN remision_id bigint,
    ADD CONSTRAINT fk_remesas_remision FOREIGN KEY (remision_id) REFERENCES remisiones (id),
    ADD CONSTRAINT uq_remesas_remision_id UNIQUE (remision_id);

-- 4) Anular Remision: Administrador, o un usuario con este permiso (Permisos).
ALTER TABLE permisos DROP CONSTRAINT ck_permisos_pantalla;
ALTER TABLE permisos ADD CONSTRAINT ck_permisos_pantalla CHECK (pantalla IN
    ('ANULAR_DOCUMENTO', 'ACCESO_TABLAS', 'ACTUALIZAR_ANUNCIOS', 'REGISTRO_CONTROL', 'HISTORIAL', 'CORREGIR_COMPRA',
     'ANULAR_REMISION'));

-- 5) La anulacion queda en el Historial.
ALTER TABLE historial_cambios DROP CONSTRAINT ck_historial_cambios_entidad;
ALTER TABLE historial_cambios ADD CONSTRAINT ck_historial_cambios_entidad CHECK (entidad IN
    ('REGISTRO_CONTROL', 'ANUNCIO', 'USUARIO', 'AGENCIA', 'PERMISO', 'TERCERO', 'REMESA', 'COMPRA_ANUNCIADA', 'COMPRA',
     'REMISION'));
ALTER TABLE historial_cambios DROP CONSTRAINT ck_historial_cambios_accion;
ALTER TABLE historial_cambios ADD CONSTRAINT ck_historial_cambios_accion CHECK (accion IN
    ('CREAR', 'MODIFICAR', 'REASIGNAR', 'ACTIVAR', 'DESACTIVAR', 'PUBLICAR', 'CONCEDER', 'RETIRAR', 'DESMARCAR', 'RESTAURAR',
     'CORREGIR', 'ANULAR'));
