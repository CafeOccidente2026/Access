-- Crea municipality (no existia en V1) y agrega la relacion users -> municipality.

CREATE TABLE municipality (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

ALTER TABLE users
    ADD COLUMN municipality_id BIGINT NOT NULL REFERENCES municipality (id);

INSERT INTO municipality (name) VALUES ('Ancuya'), ('Buesaco');
