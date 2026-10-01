-- Modulo Suministros (Access "MENUS SUMINISTROS"). Una tabla por tabla de Access, sin mezclar:
--   cash_entry       = Caja        (Caja suministros, Ajustes Caja)
--   petty_cash_entry = CajaMenor   (Caja Menor Suministros, Gastos Caja Menor)
--   supply_entry     = Suministros (Ingresar Suministros, Cheques Girados)
--   packaging_entry  = Empaque     (Empaque Suministros, Prestamo Empaques)
-- Los pagos de las compras NO se copian aca (decision del usuario 2026-09-30, opcion a): los
-- informes los leen directo de las tablas de compras. Las consultas de Access que copiaban filas
-- entre Caja y Suministros (ActualizaCaja/Ch/Dat/Tx, ActualizaSuministros, Efectivo con cheques,
-- Cheques girados a caja, CAMBIO A EFECTIVO) se resuelven al leer, en SuppliesReportServiceImpl.
-- inflow/outflow = Entradas/Salidas (en Suministros: Valor_Suministro/Vr_Gastos_o_Comp).

CREATE TABLE cash_entry (
    id BIGSERIAL PRIMARY KEY,
    transaction_id VARCHAR(30) NOT NULL,
    agency_id BIGINT NOT NULL REFERENCES agency (id),
    fund_id BIGINT NOT NULL REFERENCES fund (id),
    entry_date DATE NOT NULL,
    id_number VARCHAR(20) NOT NULL,
    detail VARCHAR(255),
    inflow NUMERIC(15, 2) NOT NULL DEFAULT 0,
    outflow NUMERIC(15, 2) NOT NULL DEFAULT 0,
    payment_method VARCHAR(20),
    check_number INT,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE petty_cash_entry (
    id BIGSERIAL PRIMARY KEY,
    transaction_id VARCHAR(30) NOT NULL,
    agency_id BIGINT NOT NULL REFERENCES agency (id),
    fund_id BIGINT NOT NULL REFERENCES fund (id),
    entry_date DATE NOT NULL,
    id_number VARCHAR(20) NOT NULL,
    detail VARCHAR(255),
    inflow NUMERIC(15, 2) NOT NULL DEFAULT 0,
    outflow NUMERIC(15, 2) NOT NULL DEFAULT 0,
    payment_method VARCHAR(20),
    check_number INT,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE supply_entry (
    id BIGSERIAL PRIMARY KEY,
    transaction_id VARCHAR(30) NOT NULL,
    agency_id BIGINT NOT NULL REFERENCES agency (id),
    fund_id BIGINT NOT NULL REFERENCES fund (id),
    entry_date DATE NOT NULL,
    id_number VARCHAR(20) NOT NULL,
    detail VARCHAR(255),
    inflow NUMERIC(15, 2) NOT NULL DEFAULT 0,
    outflow NUMERIC(15, 2) NOT NULL DEFAULT 0,
    payment_method VARCHAR(20),
    check_number INT,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE packaging_entry (
    id BIGSERIAL PRIMARY KEY,
    transaction_id VARCHAR(30) NOT NULL,
    agency_id BIGINT NOT NULL REFERENCES agency (id),
    packaging_type VARCHAR(10) NOT NULL,
    entry_date DATE NOT NULL,
    id_number VARCHAR(20) NOT NULL,
    detail VARCHAR(255),
    inflow INT NOT NULL DEFAULT 0,
    outflow INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL
);

-- Caja.Rel_cheques de Access: una fila aca = cheque ya relacionado. source dice de que tabla viene
-- el cheque (CASH, SUPPLY o el modulo de compra), igual que inventory_movement.purchase_module.
CREATE TABLE check_relation_mark (
    source VARCHAR(20) NOT NULL,
    source_id BIGINT NOT NULL,
    PRIMARY KEY (source, source_id)
);
