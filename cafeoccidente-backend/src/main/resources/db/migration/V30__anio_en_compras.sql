-- Demo del 23-nov: el año de cada compra, para verlo en Acceso a Tablas y en las consultas. Columna generada por
-- Postgres: se llena sola con el año de fecha_compra, sigue a la fecha si esta cambia y no se puede escribir, asi que
-- nunca queda desalineada. Las compras ya guardadas la reciben al aplicar la migracion.
ALTER TABLE compras_cafe_seco
    ADD COLUMN anio smallint GENERATED ALWAYS AS (EXTRACT(YEAR FROM fecha_compra)::smallint) STORED;

ALTER TABLE compras_cafe_verde
    ADD COLUMN anio smallint GENERATED ALWAYS AS (EXTRACT(YEAR FROM fecha_compra)::smallint) STORED;

ALTER TABLE compras_pasilla
    ADD COLUMN anio smallint GENERATED ALWAYS AS (EXTRACT(YEAR FROM fecha_compra)::smallint) STORED;
