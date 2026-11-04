-- HU-54 (RF-28.4): historial de las cargas de Actualiza Cupos NESS (pedido del usuario, 2026-11-04):
-- quien cargo la lista, cuando, de que archivo y cuantos cupos se agregaron, cambiaron, quedaron igual
-- y se retiraron. La tabla cupos_ness se reemplaza completa en cada carga (como Access).
CREATE TABLE cargas_cupos_ness (
    id BIGSERIAL,
    usuario_id bigint NOT NULL,
    fecha timestamp with time zone NOT NULL DEFAULT now(),
    archivo character varying(255) NOT NULL,
    filas integer NOT NULL,
    agregados integer NOT NULL,
    cambiados integer NOT NULL,
    iguales integer NOT NULL,
    retirados integer NOT NULL,
    CONSTRAINT pk_cargas_cupos_ness PRIMARY KEY (id),
    CONSTRAINT fk_cargas_cupos_ness_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
);
CREATE INDEX ix_cargas_cupos_ness_fecha ON cargas_cupos_ness (fecha DESC);
