-- Completa green_coffee_purchase (VERDES) y husk_purchase (PASILLA), reemplazando los placeholders
-- de solo "id". Fuente de verdad: Form_VERDES.bas / VERDES.txt y Form_PASILLA.bas / PASILLA.txt.

ALTER TABLE green_coffee_purchase
    ADD COLUMN purchase_date DATE NOT NULL,
    ADD COLUMN invoice_number INT NOT NULL UNIQUE,
    ADD COLUMN agency_id BIGINT NOT NULL REFERENCES agency (id),
    ADD COLUMN fund_id BIGINT NOT NULL REFERENCES fund (id),
    ADD COLUMN special_type VARCHAR(100) NOT NULL,
    ADD COLUMN product_code_id BIGINT NOT NULL REFERENCES product_code (id),
    ADD COLUMN announcement_number VARCHAR(30) NOT NULL,
    ADD COLUMN announcement_date DATE NOT NULL,
    ADD COLUMN base_price_load NUMERIC(15, 2) NOT NULL,
    ADD COLUMN id_number VARCHAR(30) NOT NULL,
    ADD COLUMN first_name VARCHAR(100) NOT NULL,
    ADD COLUMN last_name VARCHAR(100) NOT NULL,
    ADD COLUMN grower_type VARCHAR(5) NOT NULL,
    ADD COLUMN address VARCHAR(200) NOT NULL,
    ADD COLUMN cellphone VARCHAR(30) NOT NULL,
    ADD COLUMN bags_count INT NOT NULL,
    ADD COLUMN gross_kg NUMERIC(10, 2) NOT NULL,
    ADD COLUMN tare_kg NUMERIC(10, 2) NOT NULL,
    ADD COLUMN green_kg NUMERIC(10, 2) NOT NULL,
    ADD COLUMN net_kg NUMERIC(10, 2) NOT NULL,
    ADD COLUMN healthy_unit_price NUMERIC(15, 2) NOT NULL,
    ADD COLUMN defective_unit_price NUMERIC(15, 2) NOT NULL,
    ADD COLUMN bonus NUMERIC(15, 2) NOT NULL,
    ADD COLUMN costs NUMERIC(15, 2) NOT NULL,
    ADD COLUMN penalty NUMERIC(15, 2) NOT NULL,
    ADD COLUMN comp_kg_price NUMERIC(15, 2) NOT NULL,
    ADD COLUMN unit_price NUMERIC(15, 2) NOT NULL,
    ADD COLUMN gross_value NUMERIC(15, 2) NOT NULL,
    ADD COLUMN inventory_value NUMERIC(15, 2) NOT NULL,
    ADD COLUMN associate_contribution NUMERIC(15, 2) NOT NULL,
    ADD COLUMN cooperative_discount NUMERIC(15, 2) NOT NULL,
    ADD COLUMN withholding_exempt BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN withholding NUMERIC(15, 2) NOT NULL,
    ADD COLUMN shrinkage_discount NUMERIC(15, 2) NOT NULL,
    ADD COLUMN other_discounts NUMERIC(15, 2) NOT NULL,
    ADD COLUMN net_to_pay NUMERIC(15, 2) NOT NULL,
    ADD COLUMN payment_method VARCHAR(20) NOT NULL,
    ADD COLUMN check_number VARCHAR(30),
    ADD COLUMN created_by_user_id BIGINT NOT NULL,
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL;

ALTER TABLE husk_purchase
    ADD COLUMN purchase_date DATE NOT NULL,
    ADD COLUMN invoice_number INT NOT NULL UNIQUE,
    ADD COLUMN agency_id BIGINT NOT NULL REFERENCES agency (id),
    ADD COLUMN fund_id BIGINT NOT NULL REFERENCES fund (id),
    ADD COLUMN special_type VARCHAR(100) NOT NULL,
    ADD COLUMN product_code_id BIGINT NOT NULL REFERENCES product_code (id),
    ADD COLUMN announcement_number VARCHAR(30) NOT NULL,
    ADD COLUMN announcement_date DATE NOT NULL,
    ADD COLUMN base_price_dry_load NUMERIC(15, 2) NOT NULL,
    ADD COLUMN id_number VARCHAR(30) NOT NULL,
    ADD COLUMN first_name VARCHAR(100) NOT NULL,
    ADD COLUMN last_name VARCHAR(100) NOT NULL,
    ADD COLUMN grower_type VARCHAR(5) NOT NULL,
    ADD COLUMN address VARCHAR(200) NOT NULL,
    ADD COLUMN cellphone VARCHAR(30) NOT NULL,
    ADD COLUMN point_price NUMERIC(15, 2) NOT NULL,
    ADD COLUMN costs NUMERIC(15, 2) NOT NULL,
    ADD COLUMN almond_weight NUMERIC(10, 2) NOT NULL,
    ADD COLUMN almond_percentage NUMERIC(6, 2) NOT NULL,
    ADD COLUMN bags_count INT NOT NULL,
    ADD COLUMN gross_kg NUMERIC(10, 2) NOT NULL,
    ADD COLUMN tare_kg NUMERIC(10, 2) NOT NULL,
    ADD COLUMN net_kg NUMERIC(10, 2) NOT NULL,
    ADD COLUMN unit_price NUMERIC(15, 2) NOT NULL,
    ADD COLUMN gross_value NUMERIC(15, 2) NOT NULL,
    ADD COLUMN inventory_value NUMERIC(15, 2) NOT NULL,
    ADD COLUMN associate_contribution NUMERIC(15, 2) NOT NULL,
    ADD COLUMN cooperative_discount NUMERIC(15, 2) NOT NULL,
    ADD COLUMN withholding_exempt BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN withholding NUMERIC(15, 2) NOT NULL,
    ADD COLUMN shrinkage_discount NUMERIC(15, 2) NOT NULL,
    ADD COLUMN other_discounts NUMERIC(15, 2) NOT NULL,
    ADD COLUMN net_to_pay NUMERIC(15, 2) NOT NULL,
    ADD COLUMN payment_method VARCHAR(20) NOT NULL,
    ADD COLUMN check_number VARCHAR(30),
    ADD COLUMN created_by_user_id BIGINT NOT NULL,
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL;

-- Cod_Prod de VERDES: constante (DefaultValue="0110002000004" en VERDES.txt, sin AfterUpdate por
-- Especial/Fondo). Solo Fondo RP tiene evidencia; CV no aplica a LF.
INSERT INTO product_code (special_type, fund_id, code) VALUES
    ('CV', (SELECT id FROM fund WHERE code = 'RP'), '0110002000004');

-- Cod_Prod de PASILLA: Cuadro_combinado61_AfterUpdate en Form_PASILLA.bas (ambos fondos evidenciados,
-- aunque hoy solo RP es alcanzable desde el combo fijo).
INSERT INTO product_code (special_type, fund_id, code) VALUES
    ('PASILLA', (SELECT id FROM fund WHERE code = 'RP'), '0110002000008'),
    ('PASILLA', (SELECT id FROM fund WHERE code = 'LF'), '0110001000013');

-- Anuncios de prueba para poder ejercitar VERDES y PASILLA end-to-end (una fila por agencia con
-- ControlRecord activo, igual que Cafe Seco ya tiene para agencia_id 1 y 4).
INSERT INTO announcement (
    announcement_number, announcement_date, base_price_load, defective_unit_price, healthy_unit_price,
    bonus, costs, agency_id, fund_id, special_type
)
SELECT '1', CURRENT_DATE, 1200000.00, 0, 1200000.00, 0, 692.00, cr.agency_id,
       (SELECT id FROM fund WHERE code = 'RP'), special
FROM control_record cr, unnest(ARRAY['CV', 'PASILLA']) AS special;
