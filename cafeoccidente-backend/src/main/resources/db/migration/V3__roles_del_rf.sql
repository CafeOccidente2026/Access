-- HU-01: los tres roles del RF (RF-01, RN-01.1 a RN-01.3). Se renombran en el lugar: usuarios apunta a
-- rol_id, asi que cada usuario conserva su rol (D11 del mapeo).
UPDATE roles SET nombre = 'ADMINISTRADOR' WHERE nombre = 'ADMIN';
UPDATE roles SET nombre = 'USUARIO' WHERE nombre = 'USER';
INSERT INTO roles (nombre) VALUES ('OPERACIONES');
