-- Reemplaza la tabla placeholder de future_purchase (V1) por las columnas reales del formulario
-- "Compras a Futuro" (Access "COMPRAS A FUTURO", Form_COMPRAS A FUTURO.bas): un compromiso de
-- ENTREGA futura de café a un precio ya pactado hoy - NO es una compra con pago (no hay Vr_Kilo/
-- Vr_Bruto/Factura/Retefuente en el VBA real, solo el registro del compromiso + una carta-
-- manifiesto). El pago real de lo entregado se liquida despues en FERTIFUTURO (modulo aparte,
-- pendiente). Sin numeracion de factura: este formulario nunca llama ninguna macro de asignar
-- factura, solo imprime el reporte "Manifiesto".
DROP TABLE future_purchase;

CREATE TABLE future_purchase (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL,
    agency_id BIGINT NOT NULL REFERENCES agency (id),
    announcement_number VARCHAR(30) NOT NULL,
    announcement_date DATE NOT NULL,
    id_number VARCHAR(30) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    grower_type VARCHAR(1) NOT NULL,
    address VARCHAR(200) NOT NULL,
    special_type VARCHAR(100) NOT NULL,
    announced_kg NUMERIC(10, 2) NOT NULL,
    remaining_kg NUMERIC(10, 2) NOT NULL,
    delivery_date DATE NOT NULL,
    finca VARCHAR(100),
    municipality VARCHAR(100),
    vereda VARCHAR(100),
    healthy_unit_price NUMERIC(15, 2) NOT NULL,
    defective_unit_price NUMERIC(15, 2) NOT NULL,
    bonus NUMERIC(15, 2) NOT NULL,
    costs NUMERIC(15, 2) NOT NULL,
    quality_increment NUMERIC(15, 2) NOT NULL,
    base_price_load NUMERIC(15, 2) NOT NULL,
    created_by_user_id BIGINT NOT NULL
);
