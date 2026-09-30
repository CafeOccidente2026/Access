-- Factor de salida (INVENTARIO.PorcAlmSanaSl = frpond de la consulta Sld3: promedio ponderado del
-- Cod_Prod al momento de la salida). Nulo en las lineas anteriores, que se valoraron con la entrada
-- origen y se dejan como estan.
ALTER TABLE remission_line ADD COLUMN exit_percentage NUMERIC(12, 6);
