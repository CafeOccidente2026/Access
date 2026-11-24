-- 25-nov: la migracion carga los cheques de Access tal cual, sin rellenar con ceros, y Access tiene cheques de 1 a 3
-- digitos (52 en Samaniego). La base acepta de 1 a 15 digitos; la pantalla y el backend siguen exigiendo de 4 a 15
-- para los cheques nuevos (PurchasePaymentServiceImpl, SuppliesEntryServiceImpl, check-number.ts).
ALTER TABLE formas_pago_compras DROP CONSTRAINT ck_formas_pago_compras_numero_cheque;
ALTER TABLE formas_pago_compras
    ADD CONSTRAINT ck_formas_pago_compras_numero_cheque CHECK (numero_cheque ~ '^[0-9]{1,15}$');

ALTER TABLE movimientos_caja DROP CONSTRAINT ck_movimientos_caja_numero_cheque;
ALTER TABLE movimientos_caja
    ADD CONSTRAINT ck_movimientos_caja_numero_cheque CHECK (numero_cheque ~ '^[0-9]{1,15}$');

ALTER TABLE suministros DROP CONSTRAINT ck_suministros_numero_cheque;
ALTER TABLE suministros
    ADD CONSTRAINT ck_suministros_numero_cheque CHECK (numero_cheque ~ '^[0-9]{1,15}$');

ALTER TABLE movimientos_caja_menor DROP CONSTRAINT ck_movimientos_caja_menor_numero_cheque;
ALTER TABLE movimientos_caja_menor
    ADD CONSTRAINT ck_movimientos_caja_menor_numero_cheque CHECK (numero_cheque ~ '^[0-9]{1,15}$');
