-- En Access el Id de transaccion se digitaba libre y hay Id de mas de 30 caracteres (Caja Menor de Buesaco 1, 38).
-- Se amplia a 60 en las 4 tablas para migrarlos tal cual (decision del usuario, 2026-11-09). Solo amplia: no
-- cambia ningun dato.
ALTER TABLE movimientos_caja ALTER COLUMN id_transaccion TYPE character varying(60);
ALTER TABLE suministros ALTER COLUMN id_transaccion TYPE character varying(60);
ALTER TABLE movimientos_caja_menor ALTER COLUMN id_transaccion TYPE character varying(60);
ALTER TABLE empaques ALTER COLUMN id_transaccion TYPE character varying(60);
