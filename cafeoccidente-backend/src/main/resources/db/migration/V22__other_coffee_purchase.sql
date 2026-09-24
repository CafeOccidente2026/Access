-- Reemplaza la tabla placeholder de other_coffee_purchase (V1) por las columnas reales del
-- formulario "Cafés Otros" (Access "COMPRASESP", Form_COMPRASESP.bas): mismo layout que
-- dry_coffee_purchase (V5), reusa Fund (RP/LF, ya sembrado con product_code) para la familia de
-- producto en vez de una columna propia - Cuadro combinado37 en el VBA es el mismo control
-- "Fondo" que ya existe en Cafe Seco/Verde/Pasilla, con valores RP o LF.
DROP TABLE other_coffee_purchase;

CREATE TABLE other_coffee_purchase (
    id BIGSERIAL PRIMARY KEY,
    purchase_date DATE NOT NULL,
    invoice_number INT NOT NULL UNIQUE,
    agency_id BIGINT NOT NULL REFERENCES agency (id),
    fund_id BIGINT NOT NULL REFERENCES fund (id),
    special_type VARCHAR(100) NOT NULL,
    product_code_id BIGINT NOT NULL REFERENCES product_code (id),
    announcement_number VARCHAR(30) NOT NULL,
    announcement_date DATE NOT NULL,
    base_price_load NUMERIC(15, 2) NOT NULL,
    id_number VARCHAR(30) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    grower_type VARCHAR(1) NOT NULL,
    address VARCHAR(200) NOT NULL,
    cellphone VARCHAR(30) NOT NULL,
    bags_count INT NOT NULL,
    gross_kg NUMERIC(10, 2) NOT NULL,
    tare_kg NUMERIC(10, 2) NOT NULL,
    net_kg NUMERIC(10, 2) NOT NULL,
    total_stored_weight NUMERIC(10, 2) NOT NULL,
    waste_percentage NUMERIC(6, 2) NOT NULL,
    defective_stored_weight NUMERIC(10, 2) NOT NULL,
    defective_percentage NUMERIC(6, 2) NOT NULL,
    healthy_stored_weight NUMERIC(10, 2) NOT NULL,
    healthy_percentage NUMERIC(6, 2) NOT NULL,
    healthy_unit_price NUMERIC(15, 2) NOT NULL,
    defective_unit_price NUMERIC(15, 2) NOT NULL,
    bonus NUMERIC(15, 2) NOT NULL,
    penalty NUMERIC(15, 2) NOT NULL,
    costs NUMERIC(15, 2) NOT NULL,
    unit_price NUMERIC(15, 2) NOT NULL,
    gross_value NUMERIC(15, 2) NOT NULL,
    inventory_value NUMERIC(15, 2) NOT NULL,
    associate_contribution NUMERIC(15, 2) NOT NULL,
    cooperative_discount NUMERIC(15, 2) NOT NULL,
    withholding_exempt BOOLEAN NOT NULL,
    withholding NUMERIC(15, 2) NOT NULL,
    freight_discount NUMERIC(15, 2) NOT NULL,
    other_discounts NUMERIC(15, 2) NOT NULL,
    net_to_pay NUMERIC(15, 2) NOT NULL,
    payment_method VARCHAR(20) NOT NULL,
    check_number VARCHAR(30),
    created_by_user_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);
