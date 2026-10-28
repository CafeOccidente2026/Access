-- 6e (Q33, Q35): lo pagado con datafono queda como cuenta por cobrar a favor del fiel de compra
-- (cedula fiel del Registro de Control), en estado PENDIENTE. Como se salda sigue pendiente del area.
-- La anulacion de la compra la pasa a ANULADA.
CREATE TABLE cuentas_por_cobrar (
    id BIGSERIAL,
    agencia_id bigint NOT NULL,
    modulo_compra character varying(10) NOT NULL,
    compra_id bigint NOT NULL,
    numero_factura integer NOT NULL,
    cedula_vendedor character varying(20) NOT NULL,
    cedula_fiel character varying(20) NOT NULL,
    valor numeric(15,2) NOT NULL,
    estado character varying(10) DEFAULT 'PENDIENTE' NOT NULL,
    creado_en timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT pk_cuentas_por_cobrar PRIMARY KEY (id),
    CONSTRAINT fk_cuentas_por_cobrar_agencia FOREIGN KEY (agencia_id) REFERENCES agencias(id),
    CONSTRAINT uq_cuentas_por_cobrar_compra UNIQUE (modulo_compra, compra_id),
    CONSTRAINT ck_cuentas_por_cobrar_modulo CHECK (modulo_compra IN ('DRY', 'GREEN', 'HUSK')),
    CONSTRAINT ck_cuentas_por_cobrar_estado CHECK (estado IN ('PENDIENTE', 'ANULADA')),
    CONSTRAINT ck_cuentas_por_cobrar_valor CHECK (valor > 0)
);
