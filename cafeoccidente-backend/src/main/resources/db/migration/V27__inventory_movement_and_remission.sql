-- Inventarios (Access "MENUS INVENTARIOS"): confirmado contra INVENTARIOTB.txt (RecordSource
-- "INVENTARIO") que cada compra real (Seco/Verde/Pasilla/Otros/Fertifuturo) genera una fila de
-- inventario (entrada), y "EXITS"/"Salidas" (Form_EXITS.bas) despacha parcial o totalmente esa
-- fila contra un destino, validando que la cantidad no supere el saldo disponible. El legado
-- guardaba la salida como columnas planas en la MISMA fila de INVENTARIO (una sola salida por
-- fila); acá se normaliza a una tabla de lineas (remission_line) para soportar despachos
-- parciales de una misma entrada sin perder el historial, sin cambiar la regla de negocio real
-- (Cantidad <= Saldo, Vr_Salida = Valor_unitario * Cantidad).
--
-- purchase_module/purchase_id: referencia poliforma a la compra origen (Seco/Verde/Pasilla/Otros/
-- Fertifuturo viven en 5 tablas distintas) - mas simple que 5 columnas FK nullable.
DROP TABLE driver;
DROP TABLE inventory_movement;
DROP TABLE remission;

CREATE TABLE inventory_movement (
    id BIGSERIAL PRIMARY KEY,
    purchase_module VARCHAR(20) NOT NULL,
    purchase_id BIGINT NOT NULL,
    agency_id BIGINT NOT NULL REFERENCES agency (id),
    product_code_id BIGINT NOT NULL REFERENCES product_code (id),
    special_type VARCHAR(100) NOT NULL,
    invoice_number INT NOT NULL,
    purchase_date DATE NOT NULL,
    sacos INT,
    gross_kg NUMERIC(10, 2),
    net_kg NUMERIC(10, 2) NOT NULL,
    remaining_kg NUMERIC(10, 2) NOT NULL,
    healthy_percentage NUMERIC(6, 2),
    inventory_value NUMERIC(15, 2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (purchase_module, purchase_id)
);

CREATE TABLE remission (
    id BIGSERIAL PRIMARY KEY,
    remission_number INT NOT NULL,
    agency_id BIGINT NOT NULL REFERENCES agency (id),
    remission_date DATE NOT NULL,
    destination VARCHAR(200),
    conductor_id BIGINT REFERENCES grower (id),
    exported BOOLEAN NOT NULL DEFAULT FALSE,
    created_by_user_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (agency_id, remission_number)
);

CREATE TABLE remission_line (
    id BIGSERIAL PRIMARY KEY,
    remission_id BIGINT NOT NULL REFERENCES remission (id),
    inventory_movement_id BIGINT NOT NULL REFERENCES inventory_movement (id),
    quantity NUMERIC(10, 2) NOT NULL,
    unit_value NUMERIC(15, 2) NOT NULL,
    output_value NUMERIC(15, 2) NOT NULL
);
