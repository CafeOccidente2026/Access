-- ControlRecord es en realidad un registro por agencia (RegControl en Access), pero la tabla no lo
-- modelaba asi: con 2+ filas activas (una por agencia) findByActiveTrue() rompia con
-- NonUniqueResultException (403/500 en Compras Cafe Seco). Se agrega agency_id y se migra el dato
-- existente por prefix (la misma clave natural que ya usa scripts/migrate_eltambo.py).

ALTER TABLE control_record
    ADD COLUMN agency_id BIGINT REFERENCES agency (id);

UPDATE control_record SET agency_id = (SELECT id FROM agency WHERE name = 'Buesaco') WHERE prefix = 'SDBU';
UPDATE control_record SET agency_id = (SELECT id FROM agency WHERE name = 'El Tambo') WHERE prefix = 'SDTA';

ALTER TABLE control_record
    ALTER COLUMN agency_id SET NOT NULL;

-- Un solo ControlRecord activo por agencia a la vez (evita que este bug se repita en silencio).
CREATE UNIQUE INDEX control_record_agency_active_uq ON control_record (agency_id) WHERE active;
