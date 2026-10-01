-- Desglose de pago de compras migradas de Access que se pagaron con mas de una forma a la vez
-- (COMPRAS.FPef / FPch / FPtx / FPdat). Solo lo leen los informes de Suministros, para armar Caja y
-- Suministros como ActualizaCaja / ActualizaCh / ActualizaTx / ActualizaDat / ActualizaSuministros
-- (una fila por forma con monto > 0). Lo llena scripts/migrate_eltambo.py; Compras no lo escribe ni lo
-- lee: una compra sin fila aca se informa con su payment_method y net_to_pay, como siempre.
CREATE TABLE purchase_payment_split (
    source VARCHAR(20) NOT NULL,
    source_id BIGINT NOT NULL,
    cash_amount NUMERIC(15, 2) NOT NULL DEFAULT 0,
    check_amount NUMERIC(15, 2) NOT NULL DEFAULT 0,
    transfer_amount NUMERIC(15, 2) NOT NULL DEFAULT 0,
    card_amount NUMERIC(15, 2) NOT NULL DEFAULT 0,
    check_number INT,
    PRIMARY KEY (source, source_id)
);
