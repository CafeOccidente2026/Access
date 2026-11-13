-- S2 (seguridad): la app no se conecta como superusuario.
--   * migrador: dueño del esquema; solo lo usa Flyway (y los respaldos).
--   * app: lee y escribe filas; sin DDL, sin superusuario, sin crear bases ni roles.
-- Idempotente: sirve para una base nueva o para una que ya tiene las tablas. Se corre como superusuario:
--   psql -v ON_ERROR_STOP=1 -v migrador=NOMBRE -v clave_migrador=CLAVE -v app=NOMBRE -v clave_app=CLAVE \
--        -d BASE -f roles-bd.sql

SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'migrador', :'clave_migrador')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'migrador') \gexec
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'app', :'clave_app')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'app') \gexec

ALTER ROLE :"migrador" NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
ALTER ROLE :"app" NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS NOINHERIT;
SELECT format('GRANT CONNECT ON DATABASE %I TO %I, %I', current_database(), :'migrador', :'app') \gexec

-- El migrador es dueño del esquema y de lo que ya existe. ALTER TABLE también pasa las secuencias de sus columnas.
ALTER SCHEMA public OWNER TO :"migrador";
SELECT format('ALTER TABLE public.%I OWNER TO %I', tablename, :'migrador')
FROM pg_tables WHERE schemaname = 'public' \gexec
SELECT format('ALTER SEQUENCE public.%I OWNER TO %I', c.relname, :'migrador')
FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname = 'public' AND c.relkind = 'S'
  AND NOT EXISTS (SELECT 1 FROM pg_depend d WHERE d.objid = c.oid AND d.deptype IN ('a', 'i')) \gexec

-- Nadie más crea objetos en el esquema.
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO :"app";

-- La app: filas de las tablas y secuencias, hoy y en las tablas que cree Flyway mañana.
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO :"app";
GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO :"app";
ALTER DEFAULT PRIVILEGES FOR ROLE :"migrador" IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO :"app";
ALTER DEFAULT PRIVILEGES FOR ROLE :"migrador" IN SCHEMA public GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO :"app";

-- El historial de Flyway solo lo escribe el migrador.
SELECT format('REVOKE INSERT, UPDATE, DELETE ON public.flyway_schema_history FROM %I', :'app')
WHERE EXISTS (SELECT 1 FROM pg_tables WHERE schemaname = 'public' AND tablename = 'flyway_schema_history') \gexec
