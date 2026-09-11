-- Lo que el negocio llama "municipio" es el mismo concepto que "Agencia" (purchases.shared.Agency):
-- confirmado contra docs/legacy-postgres-reference (14_reg_control.sql, 41_anuncios.sql,
-- 54_agregar_mapeo_agencia_puntos_compra.sql, 64_cargar_agencia_prefijo_19_puntos.sql). Se fusiona
-- Municipality dentro de Agency en vez de mantener dos catalogos para la misma cosa.

ALTER TABLE agency
    ADD COLUMN access_agency_value VARCHAR(50),
    ADD COLUMN invoice_prefix VARCHAR(50);

-- El nombre bonito ("Buesaco") no coincide con el valor real que Access escribe en el campo
-- "agencia" de COMPRAS/Anuncios/RegControl ("BUESACO1 OCCIDENTE"). La fila ya sembrada en V4
-- usaba ese valor real como "name"; se corrige para separar ambos.
UPDATE agency
SET name = 'Buesaco', access_agency_value = 'BUESACO1 OCCIDENTE', invoice_prefix = 'SDBU'
WHERE name = 'BUESACO1 OCCIDENTE';

-- Los demas puntos de compra reales de la cooperativa (agencia_access = nombre en mayusculas,
-- salvo Buesaco). Confirmado con el administrador via el dump de referencia.
INSERT INTO agency (name, access_agency_value, invoice_prefix) VALUES
    ('Ancuya', 'ANCUYA', 'DCAN'),
    ('Consaca', 'CONSACA', 'SDCO'),
    ('El Tambo', 'EL TAMBO', 'SDTA'),
    ('La Florida', 'LA FLORIDA', 'SDFL'),
    ('Linares', 'LINARES', 'SDLI'),
    ('Pasto Sede', 'PASTO', 'SDPA'),
    ('Pasto Las Lunas', 'PASTO LAS LUNAS', 'SDLU'),
    ('Samaniego', 'SAMANIEGO', 'SDSSA'),
    ('Sandoná', 'SANDONA', 'SDSN'),
    ('Los Andes (Sotomayor)', 'LOS ANDES', 'SDSO'),
    ('Chachagüí', 'CHACHAGUI', 'DCH'),
    ('El Ingenio (Sandoná)', 'EL INGENIO', 'SDIN'),
    ('El Peñol', 'EL PEÑOL', 'SDPE'),
    ('Matituy', 'MATITUY', 'SDMA'),
    ('Tunja', 'TUNJA', 'SDTJ'),
    ('Santa María (Buesaco)', 'SANTA MARIA', 'SDSM'),
    ('Yacuanquer', 'YACUANQUER', 'SDYA'),
    ('Funes', 'FUNES', 'SDFU'),
    ('Mallama', 'MALLAMA', 'SDML');

ALTER TABLE agency
    ALTER COLUMN access_agency_value SET NOT NULL,
    ALTER COLUMN invoice_prefix SET NOT NULL,
    ADD CONSTRAINT agency_access_agency_value_key UNIQUE (access_agency_value);

-- users: municipality_id -> agency_id, emparejando por nombre (los municipios sembrados en V3
-- comparten nombre con la agencia equivalente sembrada arriba).
ALTER TABLE users ADD COLUMN agency_id BIGINT REFERENCES agency (id);

UPDATE users
SET agency_id = agency.id
FROM municipality, agency
WHERE users.municipality_id = municipality.id
  AND agency.name = municipality.name;

ALTER TABLE users
    ALTER COLUMN agency_id SET NOT NULL,
    DROP COLUMN municipality_id;

-- dry_coffee_purchase.municipality_id quedaba redundante con dry_coffee_purchase.agency_id
-- (ambos apuntaban al mismo concepto de negocio una vez fusionado Municipality en Agency).
ALTER TABLE dry_coffee_purchase DROP COLUMN municipality_id;

DROP TABLE municipality;
