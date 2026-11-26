-- 26-nov (aval del area): registro de anulaciones para auditoria. Cada anulacion de compra (seco, verde, pasilla; las
-- compras anunciadas y de cupo se anulan como compras) y de remision deja una fila con quien, cuando, por que y los
-- valores del documento antes de anularlo. Las anuladas de Access llegan con la migracion (origen ACCESS), sin
-- usuario, fecha ni motivo, porque Access no los guarda.
CREATE TABLE anulaciones (
    id BIGSERIAL,
    origen character varying(10) NOT NULL,
    tipo_documento character varying(20) NOT NULL,
    numero_documento character varying(40) NOT NULL,
    agencia_id bigint NOT NULL,
    documento_id bigint,
    anulado_en timestamp with time zone,
    usuario_id bigint,
    motivo character varying(500),
    valores jsonb NOT NULL DEFAULT '{}'::jsonb,
    nota character varying(200),
    registrado_en timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT pk_anulaciones PRIMARY KEY (id),
    CONSTRAINT fk_anulaciones_agencia FOREIGN KEY (agencia_id) REFERENCES agencias(id),
    CONSTRAINT fk_anulaciones_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
    CONSTRAINT ck_anulaciones_origen CHECK (origen IN ('WEB', 'ACCESS')),
    CONSTRAINT ck_anulaciones_tipo_documento
        CHECK (tipo_documento IN ('COMPRA_SECO', 'COMPRA_VERDE', 'COMPRA_PASILLA', 'REMISION')),
    -- Una anulacion de la web siempre tiene usuario, fecha y un motivo de al menos 10 caracteres.
    CONSTRAINT ck_anulaciones_web CHECK (origen <> 'WEB' OR (usuario_id IS NOT NULL AND anulado_en IS NOT NULL
        AND motivo IS NOT NULL AND char_length(btrim(motivo)) >= 10))
);

CREATE INDEX ix_anulaciones_agencia_anulado_en ON anulaciones (agencia_id, anulado_en);

-- Solo se inserta: nadie (ni el Administrador, ni la aplicacion, ni el dueno de la tabla) modifica ni borra.
CREATE FUNCTION anulaciones_solo_insertar() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'El registro de anulaciones solo admite insertar (% no permitido)', TG_OP;
END
$$;

CREATE TRIGGER tg_anulaciones_sin_cambios BEFORE UPDATE OR DELETE ON anulaciones
    FOR EACH ROW EXECUTE FUNCTION anulaciones_solo_insertar();
CREATE TRIGGER tg_anulaciones_sin_truncate BEFORE TRUNCATE ON anulaciones
    FOR EACH STATEMENT EXECUTE FUNCTION anulaciones_solo_insertar();

-- 26-nov (Pasto, opcion A): una salida historica de Access puede no tener numero ("SEBASTIAN ORTIZ"); se guarda con
-- su texto tal cual en numero_remision y sin numero. Solo las de Access (de_access, V33); el contador las ignora.
ALTER TABLE remisiones ALTER COLUMN numero_remision_anterior DROP NOT NULL;
ALTER TABLE remisiones ADD CONSTRAINT ck_remisiones_numero_o_de_access
    CHECK (numero_remision_anterior IS NOT NULL OR de_access);
