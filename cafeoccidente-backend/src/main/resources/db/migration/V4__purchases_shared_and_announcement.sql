-- Completa agency, fund, product_code, control_record y announcement; carga datos semilla
-- necesarios para poder probar el formulario de Compras Cafe Seco de punta a punta.

ALTER TABLE agency
    ADD COLUMN name VARCHAR(150) NOT NULL UNIQUE,
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE fund
    ADD COLUMN code VARCHAR(10) NOT NULL UNIQUE,
    ADD COLUMN name VARCHAR(100) NOT NULL,
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE product_code
    ADD COLUMN special_type VARCHAR(100) NOT NULL,
    ADD COLUMN fund_id BIGINT NOT NULL REFERENCES fund (id),
    ADD COLUMN code VARCHAR(20) NOT NULL UNIQUE;

ALTER TABLE control_record
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN control_number INT NOT NULL,
    ADD COLUMN base_factor INT NOT NULL,
    ADD COLUMN base_withholding NUMERIC(15, 2) NOT NULL,
    ADD COLUMN base_load INT NOT NULL,
    ADD COLUMN withholding_percentage NUMERIC(5, 2) NOT NULL,
    ADD COLUMN base_husk NUMERIC(10, 2) NOT NULL,
    ADD COLUMN avg_husk_percentage NUMERIC(5, 2) NOT NULL,
    ADD COLUMN purchase_point VARCHAR(150) NOT NULL,
    ADD COLUMN prefix VARCHAR(20) NOT NULL,
    ADD COLUMN costs NUMERIC(15, 2) NOT NULL,
    ADD COLUMN sample_size NUMERIC(10, 2) NOT NULL,
    ADD COLUMN excelso_kg NUMERIC(10, 2) NOT NULL,
    ADD COLUMN green_coffee_percentage NUMERIC(5, 2) NOT NULL,
    ADD COLUMN specialty_threshold NUMERIC(5, 2) NOT NULL,
    ADD COLUMN associate_percentage NUMERIC(5, 2) NOT NULL,
    ADD COLUMN non_associate_discount NUMERIC(5, 2) NOT NULL,
    ADD COLUMN trusted_id VARCHAR(30) NOT NULL,
    ADD COLUMN dian_resolution VARCHAR(30) NOT NULL,
    ADD COLUMN resolution_date DATE NOT NULL,
    ADD COLUMN resolution_from INT NOT NULL,
    ADD COLUMN resolution_to INT NOT NULL,
    ADD COLUMN validity INT NOT NULL;

ALTER TABLE announcement
    ADD COLUMN announcement_number VARCHAR(30) NOT NULL,
    ADD COLUMN announcement_date DATE NOT NULL,
    ADD COLUMN base_price_load NUMERIC(15, 2) NOT NULL,
    ADD COLUMN agency_id BIGINT NOT NULL REFERENCES agency (id),
    ADD COLUMN fund_id BIGINT NOT NULL REFERENCES fund (id),
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;

-- Agencia y fondos base (evidenciados en control-record.json / VBA original).
INSERT INTO agency (name) VALUES ('BUESACO1 OCCIDENTE');
INSERT INTO fund (code, name) VALUES ('RP', 'Resolucion Propia'), ('LF', 'Libre Formacion');

-- Codigos de producto (Cod_Prod), tomados literalmente de Cuadro_combinado61_AfterUpdate.
INSERT INTO product_code (special_type, fund_id, code) VALUES
    ('RN', (SELECT id FROM fund WHERE code = 'RP'), '0110002000002'),
    ('RN', (SELECT id FROM fund WHERE code = 'LF'), '0110001000008'),
    ('RAINFOREST', (SELECT id FROM fund WHERE code = 'LF'), '0110001000016'),
    ('RAINFOREST', (SELECT id FROM fund WHERE code = 'RP'), '0110002000010'),
    ('RAIN TE', (SELECT id FROM fund WHERE code = 'RP'), '0110002000011'),
    ('RAIN TE', (SELECT id FROM fund WHERE code = 'LF'), '0110001000017'),
    ('NESS TE', (SELECT id FROM fund WHERE code = 'RP'), '0110002000012'),
    ('NESS TE', (SELECT id FROM fund WHERE code = 'LF'), '0110001000018'),
    ('NESPRESSO - FTUSA', (SELECT id FROM fund WHERE code = 'LF'), '0110001000007'),
    ('NESPRESSO - FTUSA', (SELECT id FROM fund WHERE code = 'RP'), '0110002000001'),
    ('EXPON', (SELECT id FROM fund WHERE code = 'RP'), '0110002000018'),
    ('EXPOR', (SELECT id FROM fund WHERE code = 'RP'), '0110002000019'),
    ('REGIONAL NARIÑO 4C', (SELECT id FROM fund WHERE code = 'RP'), '0110002000017'),
    ('REGIONAL NARIÑO 4C', (SELECT id FROM fund WHERE code = 'LF'), '0110001000023'),
    ('COLOMBIA TIERRA DE DIVERSIDAD NESS', (SELECT id FROM fund WHERE code = 'LF'), '0110001000033'),
    ('COLOMBIA TIERRA DE DIVERSIDAD NESS', (SELECT id FROM fund WHERE code = 'RP'), '0110002000033'),
    ('COLOMBIA TIERRA DE DIVERSIDAD RN', (SELECT id FROM fund WHERE code = 'LF'), '0110001000038'),
    ('COLOMBIA TIERRA DE DIVERSIDAD RN', (SELECT id FROM fund WHERE code = 'RP'), '0110002000038'),
    ('NESPRESSO LATE HARVEST - FTUSA', (SELECT id FROM fund WHERE code = 'LF'), '0110001000024'),
    ('NESPRESSO LATE HARVEST - FTUSA', (SELECT id FROM fund WHERE code = 'RP'), '0110002000005'),
    ('ESTANDAR FED', (SELECT id FROM fund WHERE code = 'RP'), '0110002000015'),
    ('ESTANDAR FED', (SELECT id FROM fund WHERE code = 'LF'), '0110001000021'),
    ('ESTANDAR', (SELECT id FROM fund WHERE code = 'RP'), '0110002000016'),
    ('ESTANDAR', (SELECT id FROM fund WHERE code = 'LF'), '0110001000022'),
    ('REGIONAL NARIÑO DIFERENCIADO 85 - 85.9', (SELECT id FROM fund WHERE code = 'LF'), '0110001000032'),
    ('REGIONAL NARIÑO DIFERENCIADO 85 - 85.9', (SELECT id FROM fund WHERE code = 'RP'), '0110002000032'),
    ('REGIONAL NARIÑO DIFERENCIADO 84 - 84.9', (SELECT id FROM fund WHERE code = 'RP'), '0110002000037'),
    ('REGIONAL NARIÑO DIFERENCIADO 84 - 84.9', (SELECT id FROM fund WHERE code = 'LF'), '0110001000037'),
    ('ILLY', (SELECT id FROM fund WHERE code = 'RP'), '0110002000028'),
    ('ILLY', (SELECT id FROM fund WHERE code = 'LF'), '0110001000028'),
    ('MICROLNESS', (SELECT id FROM fund WHERE code = 'RP'), '0110002000023'),
    ('MICROLOTES 88+', (SELECT id FROM fund WHERE code = 'RP'), '0110002000035'),
    ('MICROLOTES 88+', (SELECT id FROM fund WHERE code = 'LF'), '0110001000035'),
    ('MICROLOTES 86 - 87.9', (SELECT id FROM fund WHERE code = 'RP'), '0110002000036'),
    ('MICROLOTES 86 - 87.9', (SELECT id FROM fund WHERE code = 'LF'), '0110001000036'),
    ('NESPRESSO CON TAZA - FTUSA', (SELECT id FROM fund WHERE code = 'RP'), '0110002000030'),
    ('NESPRESSO CON TAZA - FTUSA', (SELECT id FROM fund WHERE code = 'LF'), '0110001000030'),
    ('NESPRESSO LATE HARVEST CON TAZA - FTUSA', (SELECT id FROM fund WHERE code = 'RP'), '0110002000031'),
    ('NESPRESSO LATE HARVEST CON TAZA - FTUSA', (SELECT id FROM fund WHERE code = 'LF'), '0110001000031');

-- Registro de control (parametros generales), tomado de control-record.json (pantalla RegControl).
INSERT INTO control_record (
    control_number, base_factor, base_withholding, base_load, withholding_percentage, base_husk,
    avg_husk_percentage, purchase_point, prefix, costs, sample_size, excelso_kg,
    green_coffee_percentage, specialty_threshold, associate_percentage, non_associate_discount,
    trusted_id, dian_resolution, resolution_date, resolution_from, resolution_to, validity
) VALUES (
    2, 94, 8379840.00, 125, 0.5, 12.5,
    6.14, 'BUESACO1 OCCIDENTE', 'SDBU', 692.00, 250.00, 70.00,
    50, 93.33, 2, 0.8,
    '1061699481', '18764112191428', DATE '2026-07-06', 27867, 31567, 24
);

-- Anuncio de prueba para poder ejercitar el formulario de Compras Cafe Seco end-to-end.
-- No hay pantalla de administracion de anuncios todavia (queda pendiente, ver README).
INSERT INTO announcement (announcement_number, announcement_date, base_price_load, agency_id, fund_id)
VALUES (
    '1',
    CURRENT_DATE,
    1200000.00,
    (SELECT id FROM agency WHERE name = 'BUESACO1 OCCIDENTE'),
    (SELECT id FROM fund WHERE code = 'RP')
);
