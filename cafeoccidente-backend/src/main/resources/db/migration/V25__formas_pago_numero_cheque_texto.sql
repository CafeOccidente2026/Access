-- Prueba del 10-nov: el cheque de las compras era integer y 0548 se guardaba 548. Como Caja, Suministros y Caja
-- Menor (V21): los ultimos 4 digitos como texto, con sus ceros. Los que hay se convierten con lpad (548 -> 0548).
ALTER TABLE formas_pago_compras
    ALTER COLUMN numero_cheque TYPE character varying(4) USING lpad(numero_cheque::text, 4, '0'),
    ADD CONSTRAINT ck_formas_pago_compras_numero_cheque CHECK (numero_cheque ~ '^[0-9]{4}$');
