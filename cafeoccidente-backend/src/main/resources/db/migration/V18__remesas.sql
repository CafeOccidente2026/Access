-- Fase 7a (HU-13, RF-11): la Remesa agrupa compras completas (como COMPRAS.Plano / COMPRAS.Remision en
-- Access) en un numero PREFIJO-FONDO-CONSECUTIVO. Es otro proceso que las Salidas de inventario
-- (remisiones), pero el numero sale del mismo consecutivo por agencia y fondo para que nunca se repita.

-- Una remesa. Las de Access (TA0942...) se cargan aparte con su numero, sin consecutivo ni empaques.
CREATE TABLE remesas (
    id BIGSERIAL,
    agencia_id bigint NOT NULL,
    fondo_id bigint NOT NULL,
    consecutivo integer,
    numero_remesa character varying(40) NOT NULL,
    especial_filtro character varying(100),
    cantidad_empaques integer,
    tipo_empaque character varying(10),
    estado character varying(12) DEFAULT 'VIGENTE' NOT NULL,
    creado_por_usuario_id bigint NOT NULL,
    creado_en timestamp with time zone NOT NULL,
    desmarcado_por_usuario_id bigint,
    desmarcado_en timestamp with time zone,
    CONSTRAINT pk_remesas PRIMARY KEY (id),
    CONSTRAINT uq_remesas_agencia_id_numero_remesa UNIQUE (agencia_id, numero_remesa),
    CONSTRAINT fk_remesas_agencia FOREIGN KEY (agencia_id) REFERENCES agencias(id),
    CONSTRAINT fk_remesas_fondo FOREIGN KEY (fondo_id) REFERENCES fondos(id),
    CONSTRAINT fk_remesas_creado_por_usuario FOREIGN KEY (creado_por_usuario_id) REFERENCES usuarios(id),
    CONSTRAINT fk_remesas_desmarcado_por_usuario FOREIGN KEY (desmarcado_por_usuario_id) REFERENCES usuarios(id),
    CONSTRAINT ck_remesas_estado CHECK (estado IN ('VIGENTE', 'DESMARCADA')),
    CONSTRAINT ck_remesas_tipo_empaque CHECK (tipo_empaque IN ('NUEVO', 'USADO')),
    CONSTRAINT ck_remesas_cantidad_empaques CHECK (cantidad_empaques > 0)
);

-- La compra queda en una remesa (NULL = pendiente de remesar). Al desmarcar vuelve a NULL.
ALTER TABLE compras_cafe_seco ADD COLUMN remesa_id bigint,
    ADD CONSTRAINT fk_compras_cafe_seco_remesa FOREIGN KEY (remesa_id) REFERENCES remesas(id);
ALTER TABLE compras_cafe_verde ADD COLUMN remesa_id bigint,
    ADD CONSTRAINT fk_compras_cafe_verde_remesa FOREIGN KEY (remesa_id) REFERENCES remesas(id);
ALTER TABLE compras_pasilla ADD COLUMN remesa_id bigint,
    ADD CONSTRAINT fk_compras_pasilla_remesa FOREIGN KEY (remesa_id) REFERENCES remesas(id);
CREATE INDEX ix_compras_cafe_seco_remesa_id ON compras_cafe_seco (remesa_id);
CREATE INDEX ix_compras_cafe_verde_remesa_id ON compras_cafe_verde (remesa_id);
CREATE INDEX ix_compras_pasilla_remesa_id ON compras_pasilla (remesa_id);

-- RN-11.5: un consecutivo por agencia y fondo, compartido por Salidas y remesas, tomado con bloqueo
-- de fila como consecutivos_factura (V12). Antes las Salidas usaban MAX + 1 sin bloqueo.
CREATE TABLE consecutivos_remision (
    agencia_id bigint NOT NULL,
    fondo_id bigint NOT NULL,
    ultimo_consecutivo integer NOT NULL,
    CONSTRAINT pk_consecutivos_remision PRIMARY KEY (agencia_id, fondo_id),
    CONSTRAINT fk_consecutivos_remision_agencia FOREIGN KEY (agencia_id) REFERENCES agencias(id),
    CONSTRAINT fk_consecutivos_remision_fondo FOREIGN KEY (fondo_id) REFERENCES fondos(id)
);

-- Semilla: el mayor consecutivo ya usado por las Salidas de cada agencia y fondo.
INSERT INTO consecutivos_remision (agencia_id, fondo_id, ultimo_consecutivo)
SELECT agencia_id, fondo_id, max(consecutivo)
FROM remisiones
WHERE fondo_id IS NOT NULL AND consecutivo IS NOT NULL
GROUP BY agencia_id, fondo_id;
