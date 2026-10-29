-- 6f (HU-25, RN-23.6): la compra de cafe seco que factura una compra anunciada apunta a ella, para que
-- la anulacion devuelva sus kilos al saldo. En Access solo se facturaban anunciadas de cafe seco
-- (formularios COMPRAS A FUTURO y ANUNCIADAS). La llena "Facturar Compras Anunciadas" (HU-33).
ALTER TABLE compras_cafe_seco ADD COLUMN compra_anunciada_id bigint,
    ADD CONSTRAINT fk_compras_cafe_seco_compra_anunciada FOREIGN KEY (compra_anunciada_id) REFERENCES compras_anunciadas(id);

CREATE INDEX ix_compras_cafe_seco_compra_anunciada_id ON compras_cafe_seco (compra_anunciada_id);
