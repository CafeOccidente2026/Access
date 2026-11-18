-- S2 (seguridad): callback de Flyway, corre despues de cada migracion. Si Flyway (rol migrador) y la app usan
-- usuarios distintos, la app no escribe el historial de Flyway: en una base nueva esa tabla la crea Flyway despues de
-- deploy/roles-bd.sql y los privilegios por defecto se la darian. Con el mismo usuario (instalaciones de antes) no hace nada.
DO $$
BEGIN
    IF '${appUser}' <> current_user THEN
        EXECUTE format('REVOKE INSERT, UPDATE, DELETE ON flyway_schema_history FROM %I', '${appUser}');
    END IF;
END
$$;
