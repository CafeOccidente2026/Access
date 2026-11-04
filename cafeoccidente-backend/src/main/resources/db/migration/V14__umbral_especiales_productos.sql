-- 6c (Q15): el umbral de cafes especiales del Registro de Control aplica solo a estos especiales.
-- La lista vive en la tabla para no fijarla en el codigo (confirmada por el area de compras):
-- NESPRESSO, NESPRESSO CON TAZA, NESPRESSO LATE HARVEST y NESPRESSO LATE HARVEST CON TAZA (- FTUSA).
ALTER TABLE productos ADD COLUMN aplica_umbral_especiales boolean NOT NULL DEFAULT false;

UPDATE productos SET aplica_umbral_especiales = true
WHERE especial IN ('NESPRESSO - FTUSA', 'NESPRESSO CON TAZA - FTUSA',
                   'NESPRESSO LATE HARVEST - FTUSA', 'NESPRESSO LATE HARVEST CON TAZA - FTUSA');
