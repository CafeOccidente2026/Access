-- D5 (fase 5a): columnas de anuncios sin uso desde el anuncio compartido. La numeracion por agencia vive
-- en anuncios_agencias; Pr Sustentacion, Bonificacion por kilo y Costos se calculan en cada compra con el
-- Registro de Control vivo de la agencia. La bonificacion de los anuncios cargados de Access ya quedo en
-- sobrepr_carga_esp (scripts/completar_sobrepr_anuncios.py). Las compras guardan su propia copia.
ALTER TABLE anuncios DROP CONSTRAINT fk_anuncios_agencia;
ALTER TABLE anuncios
    DROP COLUMN numero_anuncio,
    DROP COLUMN agencia_id,
    DROP COLUMN pr_alm_sana,
    DROP COLUMN bonificacion,
    DROP COLUMN costos;
