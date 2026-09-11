-- Pr_AlmSana, Bonificacion y Costos tambien viven en el anuncio vigente (ver
-- docs/legacy-postgres-reference/PosgreSQL/05_precio_cafe_hoy.sql: pr_alm_sana, bonificacion y
-- costos son columnas del mismo registro que pr_base_cps/pr_alm_defec, no parametros de
-- ControlRecord). Se necesitan para autocompletar Pr Sustentacion/Bonificacion/Costos al
-- confirmar "Especial" en el formulario de Compras Cafe Seco.

ALTER TABLE announcement
    ADD COLUMN healthy_unit_price NUMERIC(15, 2),
    ADD COLUMN bonus NUMERIC(15, 2),
    ADD COLUMN costs NUMERIC(15, 2);

-- Valores de ejemplo para el anuncio de prueba sembrado en V4/V8.
UPDATE announcement
SET healthy_unit_price = 12000.00, bonus = 100.00, costs = 692.00
WHERE healthy_unit_price IS NULL;

ALTER TABLE announcement
    ALTER COLUMN healthy_unit_price SET NOT NULL,
    ALTER COLUMN bonus SET NOT NULL,
    ALTER COLUMN costs SET NOT NULL;
