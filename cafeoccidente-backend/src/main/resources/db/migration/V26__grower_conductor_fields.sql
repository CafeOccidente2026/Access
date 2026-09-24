-- "Conductor" en el VBA legado (Form_Conductores.bas, EXITS.txt: campo Conductor guarda una
-- cedula) NO es una entidad propia - es el mismo Asociado/Grower, con 2 columnas que nunca se
-- migraron: Emp_Transp (empresa transportadora) y Vehiculo (placa). Confirmado: Conductores.txt
-- esta bindeado a los mismos campos que asociados_migrar.csv mas estas dos.
ALTER TABLE grower
    ADD COLUMN transport_company VARCHAR(150),
    ADD COLUMN vehicle_plate VARCHAR(20);
