-- Especial (Cod_Prod) es parte de la identidad del anuncio vigente: distintos tipos de cafe
-- especial pueden tener anuncios de precio distintos y simultaneos para la misma agencia/fondo
-- (ver docs/legacy-vba-export/eltambo/forms/AnunciosTB.txt, columna Especial de la tabla Anuncios
-- real; confirmado con docs/legacy-postgres-reference/eltambo/anuncios_migrar.csv donde el mismo
-- dia/agencia/fondo tiene varias filas con "especial" distinto y bonus distinto).

ALTER TABLE announcement
    ADD COLUMN special_type VARCHAR(100);

UPDATE announcement SET special_type = 'ESTANDAR' WHERE special_type IS NULL;

ALTER TABLE announcement
    ALTER COLUMN special_type SET NOT NULL;
