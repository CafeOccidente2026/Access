-- HU-06 (RF-05.1, RN-05.1.5): la ultima agencia que eligio el Administrador con el boton Agencias.
-- Null = todavia no eligio: trabaja en su agencia asignada (usuarios.agencia_id).
ALTER TABLE usuarios ADD COLUMN ultima_agencia_id bigint;
ALTER TABLE usuarios ADD CONSTRAINT fk_usuarios_ultima_agencia
    FOREIGN KEY (ultima_agencia_id) REFERENCES agencias(id);
