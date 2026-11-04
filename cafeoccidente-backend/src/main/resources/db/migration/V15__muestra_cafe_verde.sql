-- 6d (HU-23 CA-2): la compra de cafe verde guarda la muestra fija del Registro de Control
-- (Peso Tot Alm, Peso Tot Pasilla y Peso Alm Sana) y los porcentajes calculados, como Access en
-- COMPRAS (W_TotAlm, PorcMerma, W_AlmDefec, PorcAlmDefec, W_AlmSana, PorcAlmSana). Nulas: las compras
-- anteriores no las tienen.
ALTER TABLE compras_cafe_verde
    ADD COLUMN peso_tot_alm numeric(10,2),
    ADD COLUMN porc_merma numeric(10,2),
    ADD COLUMN peso_tot_pasilla numeric(10,2),
    ADD COLUMN porc_kg_pasilla numeric(10,2),
    ADD COLUMN peso_alm_sana numeric(10,2),
    ADD COLUMN factor numeric(10,2);
