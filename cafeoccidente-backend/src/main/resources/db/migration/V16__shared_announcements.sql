-- Anuncios compartidos entre agencias (ver docs/diseno-anuncios-compartidos.md, opcion A confirmada).
-- El admin publica UN anuncio "maestro" (precio crudo, sin agencia); cada agencia con ControlRecord
-- activo recibe su propia fila de numeracion (su propio consecutivo + su propio prefijo de
-- ControlRecord). Costos/Pr Sustentacion/Bonificacion dejan de congelarse en el anuncio: se calculan
-- en el momento de la compra con el ControlRecord VIVO de la agencia que compra (decision de negocio
-- para el sistema nuevo - el VBA legado congelaba porque cada agencia tenia su propia base Access).

-- agency_id/healthy_unit_price/bonus/costs quedan en la entidad SIN USAR por el codigo nuevo (para no
-- perder los anuncios historicos ya creados, todos de Buesaco) pero un anuncio "maestro" nuevo no los
-- completa, asi que dejan de ser obligatorios.
ALTER TABLE announcement
    ALTER COLUMN agency_id DROP NOT NULL,
    ALTER COLUMN healthy_unit_price DROP NOT NULL,
    ALTER COLUMN bonus DROP NOT NULL,
    ALTER COLUMN costs DROP NOT NULL,
    ADD COLUMN special_surcharge NUMERIC(15, 2);

-- Numeracion por agencia: una fila por cada agencia que recibe el anuncio maestro, con SU propio
-- consecutivo (el prefijo se toma de control_record.prefix al momento de mostrarlo, no se duplica
-- aqui). UNIQUE (agency_id, announcement_number) refleja que el consecutivo es por agencia, igual
-- que ya pasa con el numero de factura.
CREATE TABLE agency_announcement_number (
    id BIGSERIAL PRIMARY KEY,
    agency_id BIGINT NOT NULL REFERENCES agency (id),
    master_announcement_id BIGINT NOT NULL REFERENCES announcement (id),
    announcement_number INT NOT NULL,
    assigned_at DATE NOT NULL,
    UNIQUE (agency_id, announcement_number)
);

-- Migra los anuncios ya creados a la tabla de numeracion, conservando el numero que ya tenian.
-- announcement.agency_id NO se borra (ver arriba), asi que esta migracion es no destructiva: cada
-- fila historica queda referenciada como su propio "maestro".
--
-- Excepcion: unos pocos anuncios de prueba (creados durante este mismo proyecto, no del historial
-- real) repiten agencia+numero (p.ej. dos anuncios "1" para la misma agencia con Especial distinto),
-- algo que el UNIQUE nuevo no permite y que la numeracion vieja (MAX+1 sin distinguir fondo/especial)
-- nunca debio permitir. Para esos casos se conserva el numero original solo en la fila mas antigua
-- (la primera que existio) y las demas se renumeran a continuacion del maximo de esa agencia, sin
-- perder ninguna fila.
WITH ranked AS (
    SELECT
        id,
        agency_id,
        CAST(announcement_number AS integer) AS orig_number,
        announcement_date,
        ROW_NUMBER() OVER (
            PARTITION BY agency_id, CAST(announcement_number AS integer) ORDER BY id
        ) AS dup_rank
    FROM announcement
    WHERE agency_id IS NOT NULL
),
agency_max AS (
    SELECT agency_id, MAX(orig_number) AS max_number FROM ranked GROUP BY agency_id
),
extras AS (
    SELECT
        r.id, r.agency_id, r.announcement_date,
        m.max_number + ROW_NUMBER() OVER (PARTITION BY r.agency_id ORDER BY r.id) AS new_number
    FROM ranked r
    JOIN agency_max m ON m.agency_id = r.agency_id
    WHERE r.dup_rank > 1
)
INSERT INTO agency_announcement_number (agency_id, master_announcement_id, announcement_number, assigned_at)
SELECT agency_id, id, orig_number, announcement_date FROM ranked WHERE dup_rank = 1
UNION ALL
SELECT agency_id, id, new_number, announcement_date FROM extras;
