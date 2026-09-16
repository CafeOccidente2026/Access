-- Crea la tabla de staging que respalda LegacyNessProgram (GrowerService: lookup de Programa/Cupo
-- para los especiales NESS/NESSLH/RN4C). Los datos reales se cargan aparte con
-- scripts/migrate_eltambo.py desde ness_migrar.csv (por eso arranca vacia); sin esta migracion,
-- Hibernate falla la validacion de esquema (ddl-auto: validate) en cualquier Postgres limpio (CI).

CREATE TABLE IF NOT EXISTS staging_legacy_ness (
    id SERIAL PRIMARY KEY,
    cedula TEXT,
    nombres TEXT,
    programa TEXT,
    cupo TEXT
);
