-- Regla del area (18-nov): el saldo de un cupo de anuncio solo baja con las compras hechas por "Facturar Anuncios con
-- Cupos". Las compras normales de seco con el mismo numero de anuncio no cuentan. Las ya guardadas quedan en false.
ALTER TABLE compras_cafe_seco ADD COLUMN con_cupo boolean NOT NULL DEFAULT false;
