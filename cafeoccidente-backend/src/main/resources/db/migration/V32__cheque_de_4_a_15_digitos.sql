-- Ensayo de migracion del 24-nov: Consaca usa chequeras de 5 digitos (13324...). El cheque pasa de "los ultimos 4
-- digitos" (V21, V25) a de 4 a 15 digitos, solo numeros. Sigue siendo texto para conservar los ceros (0548 sigue
-- 0548); los cheques que ya estan cumplen la regla nueva.
ALTER TABLE formas_pago_compras DROP CONSTRAINT ck_formas_pago_compras_numero_cheque;
ALTER TABLE formas_pago_compras
    ALTER COLUMN numero_cheque TYPE character varying(15),
    ADD CONSTRAINT ck_formas_pago_compras_numero_cheque CHECK (numero_cheque ~ '^[0-9]{4,15}$');

ALTER TABLE movimientos_caja DROP CONSTRAINT ck_movimientos_caja_numero_cheque;
ALTER TABLE movimientos_caja
    ALTER COLUMN numero_cheque TYPE character varying(15),
    ADD CONSTRAINT ck_movimientos_caja_numero_cheque CHECK (numero_cheque ~ '^[0-9]{4,15}$');

ALTER TABLE suministros DROP CONSTRAINT ck_suministros_numero_cheque;
ALTER TABLE suministros
    ALTER COLUMN numero_cheque TYPE character varying(15),
    ADD CONSTRAINT ck_suministros_numero_cheque CHECK (numero_cheque ~ '^[0-9]{4,15}$');

ALTER TABLE movimientos_caja_menor DROP CONSTRAINT ck_movimientos_caja_menor_numero_cheque;
ALTER TABLE movimientos_caja_menor
    ALTER COLUMN numero_cheque TYPE character varying(15),
    ADD CONSTRAINT ck_movimientos_caja_menor_numero_cheque CHECK (numero_cheque ~ '^[0-9]{4,15}$');
