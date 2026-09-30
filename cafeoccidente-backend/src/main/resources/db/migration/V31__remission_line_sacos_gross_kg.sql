-- Sacos y Kilos Brutos de cada salida (Form_EXITS: Sacos / Kilos_Brutos; casilleros "No. Sacos" y
-- "Kilos Brutos" de DESPACHOS). Nulos: las lineas ya creadas no los tenian.
ALTER TABLE remission_line ADD COLUMN sacos INTEGER;
ALTER TABLE remission_line ADD COLUMN gross_kg NUMERIC(10, 2);
