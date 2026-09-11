-- Caficultor (tabla "Asociados" en Access). Referencia de estructura:
-- docs/legacy-postgres-reference/PosgreSQL/03_asociados.sql. No se cargan los ~27,328 registros
-- reales todavia (queda pendiente una carga masiva futura); solo se siembran 2-3 de prueba para
-- poder ejercitar el flujo de Compras Cafe Seco de punta a punta.

CREATE TABLE grower (
    id BIGSERIAL PRIMARY KEY,
    id_number VARCHAR(30) NOT NULL UNIQUE,
    first_name VARCHAR(100) NOT NULL,
    second_name VARCHAR(100),
    last_name VARCHAR(100) NOT NULL,
    second_last_name VARCHAR(100),
    agency_id BIGINT NOT NULL REFERENCES agency (id),
    birth_date DATE,
    address VARCHAR(200) NOT NULL,
    phone VARCHAR(30) NOT NULL,
    affiliation_date DATE,
    grower_type VARCHAR(1) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    deceased BOOLEAN NOT NULL DEFAULT FALSE,
    withdrawn BOOLEAN NOT NULL DEFAULT FALSE
);

INSERT INTO grower (
    id_number, first_name, second_name, last_name, second_last_name, agency_id, birth_date,
    address, phone, affiliation_date, grower_type, active, deceased, withdrawn
) VALUES
    ('123456', 'Juan', NULL, 'Perez', 'Gomez', (SELECT id FROM agency WHERE name = 'Buesaco'),
        DATE '1975-03-10', 'Vereda El Diviso', '3001234567', DATE '2005-06-01', 'S', TRUE, FALSE, FALSE),
    ('789012', 'Maria', 'Isabel', 'Rodriguez', NULL, (SELECT id FROM agency WHERE name = 'Buesaco'),
        DATE '1982-11-22', 'Vereda La Cocha', '3009876543', DATE '2010-02-15', 'C', TRUE, FALSE, FALSE),
    ('345678', 'Carlos', NULL, 'Martinez', 'Lopez', (SELECT id FROM agency WHERE name = 'Buesaco'),
        DATE '1950-07-04', 'Vereda San Isidro', '3005551234', DATE '1998-09-20', 'S', FALSE, TRUE, FALSE);
