-- El ControlRecord de El Tambo (prefix SDTA, agency 'El Tambo') solo existia en la base porque
-- scripts/migrate_eltambo.py lo inserta leyendo docs/legacy-postgres-reference/eltambo/regcontrol_migrar.csv;
-- ese script no esta enganchado a Flyway/Docker (se corre a mano), asi que un rebuild del volumen de
-- Postgres sin volver a correrlo deja la agencia sin ControlRecord, o si alguien reinserta la fila a
-- mano puede repetir el cruce de columnas ya documentado en el script (kgexcelso -> base_factor,
-- factorbase -> specialty_threshold, porcasociados -> associate_percentage, porcdesccoop ->
-- non_associate_discount). Se deja el dato correcto (validado contra regcontrol_migrar.csv y contra
-- vr_kilo real de compras_migrar.csv) como migracion versionada para que sobreviva a cualquier
-- reconstruccion futura.

UPDATE control_record
SET base_factor = 70,
    specialty_threshold = 94.00,
    associate_percentage = 2,
    non_associate_discount = 0.8
WHERE agency_id = (SELECT id FROM agency WHERE name = 'El Tambo');

INSERT INTO control_record (
    agency_id, active, control_number, base_factor, base_withholding, base_load,
    withholding_percentage, base_husk, avg_husk_percentage, purchase_point,
    prefix, costs, sample_size, excelso_kg, green_coffee_percentage,
    specialty_threshold, associate_percentage, non_associate_discount,
    trusted_id, dian_resolution, resolution_date, resolution_from, resolution_to, validity
)
SELECT
    (SELECT id FROM agency WHERE name = 'El Tambo'), true, 2, 70, 8379840.00, 125,
    0.5, 12.5, 6.14, 'EL TAMBO',
    'SDTA', 698.00, 250.00, 70.00, 50,
    94.00, 2, 0.8,
    '87304051', '18764112142534', DATE '2026-07-04', 43754, 48753, 24
WHERE NOT EXISTS (SELECT 1 FROM control_record WHERE prefix = 'SDTA');
