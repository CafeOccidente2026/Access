-- V18's UPDATE branch (para cuando el ControlRecord de El Tambo ya existia) solo tocaba base_factor,
-- specialty_threshold, associate_percentage y non_associate_discount: avg_husk_percentage se quedaba
-- con el valor que haya insertado originalmente scripts/migrate_eltambo.py (que en algunos entornos
-- no coincide con el 6.14 real de regcontrol_migrar.csv/porckgpasprom). Reproduce el cruce faltante.
UPDATE control_record
SET avg_husk_percentage = 6.14
WHERE agency_id = (SELECT id FROM agency WHERE name = 'El Tambo');
