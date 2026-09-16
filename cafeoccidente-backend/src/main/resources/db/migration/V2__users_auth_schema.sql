-- Reemplaza la tabla placeholder de users (solo id) por el esquema real de
-- autenticacion. role, permission de V1 quedan sin uso: el rol ahora vive
-- como columna string en users, con solo 2 valores fijos (ADMIN, PURCHASE_AGENT).

DROP TABLE permission;
DROP TABLE role;
DROP TABLE users;

CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(30) NOT NULL,
    full_name VARCHAR(150) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
