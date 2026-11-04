-- HU-08 (RN-07.5, RN-07.6): cedula del usuario (fiel de compra). Nullable: los usuarios que ya
-- existen no la tienen; el Administrador la agrega al editarlos.
ALTER TABLE usuarios ADD COLUMN cedula character varying(30);
ALTER TABLE usuarios ADD CONSTRAINT uq_usuarios_cedula UNIQUE (cedula);

-- D2: el prefijo vive en registros_control. Las agencias que aun no tienen Registro de Control
-- conservan el suyo como sugerido, para prellenar el campo Prefijo al crearlo.
CREATE TABLE prefijos_sugeridos (
    agencia_id bigint NOT NULL,
    prefijo character varying(20) NOT NULL,
    CONSTRAINT pk_prefijos_sugeridos PRIMARY KEY (agencia_id),
    CONSTRAINT fk_prefijos_sugeridos_agencia FOREIGN KEY (agencia_id) REFERENCES agencias(id)
);
INSERT INTO prefijos_sugeridos (agencia_id, prefijo)
SELECT a.id, a.prefijo_factura FROM agencias a
WHERE a.prefijo_factura <> ''
  AND NOT EXISTS (SELECT 1 FROM registros_control r WHERE r.agencia_id = a.id);
ALTER TABLE agencias DROP COLUMN prefijo_factura;

-- HU-09: las agencias creadas desde Usuarios no vienen de Access. nombre_access se queda porque
-- migrate_eltambo.py mapea con el.
ALTER TABLE agencias ALTER COLUMN nombre_access DROP NOT NULL;
