package com.cafeoccidente.backend.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * S2 (seguridad): la app se conecta con un rol sin superusuario y sin permisos de esquema; solo Flyway (rol
 * migrador) cambia tablas. Corre donde la conexion usa ese rol (DB_ROLES_RESTRINGIDOS=true: el CI y el compose de
 * prueba). Cada intento va en su transaccion, que se deshace: si un permiso sobrara, igual no queda nada cambiado.
 */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "DB_ROLES_RESTRINGIDOS", matches = "true")
class DatabasePrivilegesIntegrationTest {

    /** SQLSTATE de Postgres para "permiso denegado" / "debe ser dueno" (insufficient_privilege). */
    private static final String DENIED = "42501";

    @Autowired
    private JdbcTemplate jdbc;

    private static String sqlState(Throwable error) {
        Throwable cause = NestedExceptionUtils.getMostSpecificCause(error);
        return cause instanceof SQLException sql ? sql.getSQLState() : null;
    }

    private void denied(String statement) {
        assertThatThrownBy(() -> jdbc.execute(statement)).satisfies(e -> assertThat(sqlState(e)).isEqualTo(DENIED));
    }

    @Test
    void theAppIsNotASuperuserAndCannotCreateDatabasesOrRoles() {
        Map<String, Object> role = jdbc.queryForMap(
                "SELECT rolsuper, rolcreatedb, rolcreaterole, rolbypassrls FROM pg_roles WHERE rolname = current_user");
        assertThat(role).containsEntry("rolsuper", false).containsEntry("rolcreatedb", false)
                .containsEntry("rolcreaterole", false).containsEntry("rolbypassrls", false);
    }

    @Test
    void theAppCannotCreateTables() {
        denied("CREATE TABLE s2_prueba (id int)");
    }

    @Test
    void theAppCannotDropTables() {
        denied("DROP TABLE empaques");
    }

    @Test
    void theAppCannotAlterTables() {
        denied("ALTER TABLE empaques ADD COLUMN s2_prueba int");
    }

    @Test
    void theAppCannotTruncateTables() {
        denied("TRUNCATE empaques");
    }

    @Test
    void theAppCannotWriteTheFlywayHistory() {
        denied("DELETE FROM flyway_schema_history");
    }

    @Test
    void theAppStillReadsAndWritesRows() {
        Long agencies = jdbc.queryForObject("SELECT count(*) FROM agencias", Long.class);
        assertThat(agencies).isPositive();
        jdbc.update("UPDATE agencias SET nombre = nombre WHERE id = (SELECT min(id) FROM agencias)");
    }
}
