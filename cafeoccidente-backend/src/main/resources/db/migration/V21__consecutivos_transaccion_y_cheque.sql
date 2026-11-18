-- Fase 5a (RF-27, §2.1): el Id de transaccion de Caja, Suministros, Empaques y Caja Menor lo genera el sistema
-- (PREFIJO + digito + codigo + consecutivo, ej. SDBU5SUM001). Un consecutivo por agencia y codigo de pantalla
-- ('2', '3SUM', '5PRE', '7GAS'...), con el mismo bloqueo de fila que consecutivos_factura (V12). En Access el Id se
-- digitaba y hay repetidos en los datos migrados, por eso no se pone UNIQUE sobre las tablas de movimientos.
CREATE TABLE consecutivos_transaccion (
    agencia_id bigint NOT NULL,
    codigo character varying(8) NOT NULL,
    ultimo_consecutivo integer NOT NULL,
    CONSTRAINT pk_consecutivos_transaccion PRIMARY KEY (agencia_id, codigo),
    CONSTRAINT fk_consecutivos_transaccion_agencia FOREIGN KEY (agencia_id) REFERENCES agencias(id),
    CONSTRAINT ck_consecutivos_transaccion_ultimo_consecutivo CHECK (ultimo_consecutivo > 0)
);

-- RN-27.1.4 y RN-27.10.3: el cheque son los ultimos 4 digitos, con sus ceros a la izquierda (0548). Como entero el
-- cero se perdia. Los cheques que hay ya tienen 4 digitos.
ALTER TABLE movimientos_caja
    ALTER COLUMN numero_cheque TYPE character varying(4) USING lpad(numero_cheque::text, 4, '0'),
    ADD CONSTRAINT ck_movimientos_caja_numero_cheque CHECK (numero_cheque ~ '^[0-9]{4}$');
ALTER TABLE suministros
    ALTER COLUMN numero_cheque TYPE character varying(4) USING lpad(numero_cheque::text, 4, '0'),
    ADD CONSTRAINT ck_suministros_numero_cheque CHECK (numero_cheque ~ '^[0-9]{4}$');
ALTER TABLE movimientos_caja_menor
    ALTER COLUMN numero_cheque TYPE character varying(4) USING lpad(numero_cheque::text, 4, '0'),
    ADD CONSTRAINT ck_movimientos_caja_menor_numero_cheque CHECK (numero_cheque ~ '^[0-9]{4}$');
