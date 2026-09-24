-- FERTIFUTURO (Form_FERTIFUTURO.bas): liquidacion real de lo comprometido en "Compras a Futuro" -
-- a diferencia de ese formulario (que solo registra el compromiso), este SI tiene cascada de
-- precio completa (Vr_Kilo/Vr_Bruto/Retefuente/Neto_a_Pagar) y numeracion de factura propia
-- (Factura/Prefijo), comparte el mismo pool de numeracion por agencia que Seco/Verde/Pasilla/Otros
-- (ver PurchaseInvoiceNumberService). No incluye el sub-sistema de "obligacion" (abono/prestamo de
-- insumos, Form_FUTURE FERTIFUTURO.bas Texto47_AfterUpdate) - sin datos historicos ni tabla de
-- origen clara, queda fuera de esta fase (decision del usuario 2026-09-23).
CREATE TABLE ferti_futuro_purchase (
    id BIGSERIAL PRIMARY KEY,
    purchase_date DATE NOT NULL,
    invoice_number INT NOT NULL UNIQUE,
    agency_id BIGINT NOT NULL REFERENCES agency (id),
    fund_id BIGINT NOT NULL REFERENCES fund (id),
    special_type VARCHAR(100) NOT NULL,
    product_code_id BIGINT NOT NULL REFERENCES product_code (id),
    announcement_number VARCHAR(30) NOT NULL,
    announcement_date DATE NOT NULL,
    future_purchase_id BIGINT REFERENCES future_purchase (id),
    id_number VARCHAR(30) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    grower_type VARCHAR(1) NOT NULL,
    address VARCHAR(200) NOT NULL,
    sacos INT NOT NULL,
    net_kg NUMERIC(10, 2) NOT NULL,
    gross_kg NUMERIC(10, 2) NOT NULL,
    tare_kg NUMERIC(10, 2) NOT NULL,
    healthy_stored_weight NUMERIC(10, 2) NOT NULL,
    healthy_percentage NUMERIC(6, 2) NOT NULL,
    defective_stored_weight NUMERIC(10, 2) NOT NULL,
    defective_percentage NUMERIC(6, 2) NOT NULL,
    healthy_unit_price NUMERIC(15, 2) NOT NULL,
    defective_unit_price NUMERIC(15, 2) NOT NULL,
    bonus NUMERIC(15, 2) NOT NULL,
    penalty NUMERIC(15, 2) NOT NULL,
    costs NUMERIC(15, 2) NOT NULL,
    quality_increment_rate NUMERIC(15, 2) NOT NULL,
    quality_increment_amount NUMERIC(15, 2) NOT NULL,
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

-- Cod_Prod de CORR y TE (Cuadro_combinado61_AfterUpdate, Form_FERTIFUTURO.bas): confirmados
-- literalmente en el VBA, ausentes del seed de product_code hasta ahora (solo se habian migrado
-- los Especiales que ya usaban Seco/Verde/Pasilla/Otros). "NESS" en Fertifuturo reusa los codigos
-- ya sembrados bajo "NESPRESSO - FTUSA" (mismo Cod_Prod, distinta etiqueta de combo en ese
-- formulario) - no requiere fila nueva.
INSERT INTO product_code (special_type, fund_id, code) VALUES
    ('CORR', (SELECT id FROM fund WHERE code = 'RP'), '0110002000003'),
    ('CORR', (SELECT id FROM fund WHERE code = 'LF'), '0110001000009'),
    ('TE', (SELECT id FROM fund WHERE code = 'RP'), '0110002000007'),
    ('TE', (SELECT id FROM fund WHERE code = 'LF'), '0110001000012');
