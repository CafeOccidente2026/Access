-- Completa role y users con las columnas reales necesarias para autenticacion.
-- permission se deja minima (no expuesta por ningun endpoint todavia).

ALTER TABLE role
    ADD COLUMN name VARCHAR(50) NOT NULL UNIQUE;

ALTER TABLE users
    ADD COLUMN username VARCHAR(100) NOT NULL UNIQUE,
    ADD COLUMN password_hash VARCHAR(255) NOT NULL,
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN role_id BIGINT NOT NULL REFERENCES role (id);

INSERT INTO role (name) VALUES ('ADMIN'), ('USER');
