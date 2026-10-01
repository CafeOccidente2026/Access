-- grower = tabla Asociados de Access completa: caficultores, vendedores, asociaciones/fundaciones y
-- conductores son filas de la misma tabla (formularios Asociados, Vendedores, Asociaciones1 y
-- Conductores). Access permite filas sin apellido (las asociaciones guardan todo el nombre en
-- "1er Nombre") y sin agencia: se migran tal cual, sin inventar valores.
ALTER TABLE grower ALTER COLUMN last_name DROP NOT NULL;
ALTER TABLE grower ALTER COLUMN agency_id DROP NOT NULL;

-- Columnas de Asociados que faltaban. Aceptado y Habil separados: "active" (Habil AND Aceptado)
-- queda como estaba porque Compras depende de el. NumRegistro, Exportado, Nuevo y Pais tienen hoy
-- el mismo valor en todas las filas, pero los usa el programa: IngresaVendedor une por NumRegistro
-- con RegControl, "Exportar Informacion" exporta los Exportado = No y los marca, y Actualizar
-- Vendedores (actualizasocios / IngresaNuevos) copia Nuevo y Pais.
ALTER TABLE grower
    ADD COLUMN registry_number INTEGER,
    ADD COLUMN exported BOOLEAN,
    ADD COLUMN is_new BOOLEAN,
    ADD COLUMN country VARCHAR(10),
    ADD COLUMN sex VARCHAR(1),
    ADD COLUMN is_association BOOLEAN,
    ADD COLUMN accepted BOOLEAN,
    ADD COLUMN eligible BOOLEAN,
    ADD COLUMN marital_status VARCHAR(50),
    ADD COLUMN birth_place VARCHAR(100),
    ADD COLUMN coffee_id_card VARCHAR(30),
    ADD COLUMN act_number VARCHAR(30),
    ADD COLUMN observation VARCHAR(255),
    ADD COLUMN city_code VARCHAR(10),
    ADD COLUMN postal_code VARCHAR(10),
    ADD COLUMN email VARCHAR(150);

-- Compras a Futuro copia el apellido del Asociado: sin apellido en Asociados, sin apellido aca.
ALTER TABLE future_purchase ALTER COLUMN last_name DROP NOT NULL;
