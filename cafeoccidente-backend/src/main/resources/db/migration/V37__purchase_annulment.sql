-- "Anular Documento" (macro AnularFactura, formulario Cns_ComprasParaAnular). Access no borra la
-- compra: deja COMPRAS.Forma_de_Pago = "ANULADA" y los montos en 0, y borra sus copias en INVENTARIO,
-- Caja, Suministros y CVRNCOMPRAS. Aca el estado va en su propia columna (Forma_de_Pago de la web es
-- la forma de pago real) y se agrega quien y cuando anulo, que Access no guardaba.
-- Las 60 anuladas reales de El Tambo las carga scripts/migrate_eltambo.py (sin fecha ni usuario).
ALTER TABLE dry_coffee_purchase
    ADD COLUMN status VARCHAR(10) NOT NULL DEFAULT 'VALIDA' CHECK (status IN ('VALIDA', 'ANULADA')),
    ADD COLUMN annulled_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN annulled_by_user_id BIGINT REFERENCES users (id);
ALTER TABLE green_coffee_purchase
    ADD COLUMN status VARCHAR(10) NOT NULL DEFAULT 'VALIDA' CHECK (status IN ('VALIDA', 'ANULADA')),
    ADD COLUMN annulled_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN annulled_by_user_id BIGINT REFERENCES users (id);
ALTER TABLE other_coffee_purchase
    ADD COLUMN status VARCHAR(10) NOT NULL DEFAULT 'VALIDA' CHECK (status IN ('VALIDA', 'ANULADA')),
    ADD COLUMN annulled_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN annulled_by_user_id BIGINT REFERENCES users (id);
ALTER TABLE husk_purchase
    ADD COLUMN status VARCHAR(10) NOT NULL DEFAULT 'VALIDA' CHECK (status IN ('VALIDA', 'ANULADA')),
    ADD COLUMN annulled_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN annulled_by_user_id BIGINT REFERENCES users (id);
ALTER TABLE ferti_futuro_purchase
    ADD COLUMN status VARCHAR(10) NOT NULL DEFAULT 'VALIDA' CHECK (status IN ('VALIDA', 'ANULADA')),
    ADD COLUMN annulled_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN annulled_by_user_id BIGINT REFERENCES users (id);
