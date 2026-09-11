-- Pr_AlmDefec (precio almendra defectuosa) lo trae el anuncio vigente, igual que Pr_AlmSana y
-- Precio Base Carga PC - no es un parametro de ControlRecord/RegControl. Confirmado contra
-- docs/legacy-postgres-reference/05_precio_cafe_hoy.sql (pr_alm_defec es NOT NULL en la tabla
-- que reemplaza a Anuncios de Access, igual que pr_alm_sana y pr_base_cps).

ALTER TABLE announcement ADD COLUMN defective_unit_price NUMERIC(15, 2);

-- Valor de ejemplo para el anuncio de prueba sembrado en V4 (no hay pantalla de administracion de
-- anuncios todavia para cargar el valor real - ver README).
UPDATE announcement SET defective_unit_price = 8000.00 WHERE defective_unit_price IS NULL;

ALTER TABLE announcement ALTER COLUMN defective_unit_price SET NOT NULL;

-- Era el lugar equivocado (ver V6): Pr_AlmDefec no es un parametro fijo de ControlRecord.
ALTER TABLE control_record DROP COLUMN defective_almond_unit_price;
