-- 25-nov: las remisiones que carga la migracion desde Access (salidas historicas del INVENTARIO) no se pueden
-- anular en la web: Access no guarda a que facturas pertenece cada salida.
ALTER TABLE remisiones ADD COLUMN de_access boolean NOT NULL DEFAULT false;

-- Observacion de la cabecera de la remesa (la migracion anota las remesas de Access que juntaban RP y LF).
ALTER TABLE remesas ADD COLUMN observacion varchar(200);
