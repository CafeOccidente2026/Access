package com.cafeoccidente.backend.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Fase 1: el esquema public queda en español con las convenciones de CLAUDE.md (snake_case sin
 * tildes ni ñ, prefijos pk_/fk_/uq_/ck_/ix_). Solo mira public y deja fuera flyway_schema_history.
 */
@SpringBootTest
class SpanishSchemaTest {

    private static final Set<String> TABLES = Set.of(
            "agencias", "anuncios", "anuncios_agencias", "cupos_anuncios", "cheques_relacionados",
            "compras_anunciadas", "compras_cafe_seco", "compras_cafe_verde",
            "compras_pasilla", "cupos_ness", "empaques", "fondos",
            "formas_pago_compras", "inventarios", "movimientos_caja", "movimientos_caja_menor", "permisos",
            "prefijos_sugeridos",
            "productos", "registros_control", "remisiones", "remisiones_detalle", "roles", "suministros",
            "terceros", "usuarios");

    private static final String NAME = "[a-z0-9_]+";

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void publicSchemaHasExactlyTheSpanishTables() {
        assertThat(names("SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'"
                + " AND table_type = 'BASE TABLE' AND table_name <> 'flyway_schema_history'"))
                .containsExactlyInAnyOrderElementsOf(TABLES);
    }

    @Test
    void columnsAndSequencesAreSnakeCase() {
        assertThat(names("SELECT table_name || '.' || column_name FROM information_schema.columns"
                + " WHERE table_schema = 'public' AND table_name <> 'flyway_schema_history'"))
                .isNotEmpty()
                .allMatch(n -> n.matches(NAME + "\\." + NAME));
        assertThat(names("SELECT sequencename FROM pg_sequences WHERE schemaname = 'public'"))
                .allMatch(n -> n.matches(NAME));
    }

    @Test
    void constraintsAndIndexesUseConventionPrefixes() {
        // Los NOT NULL (contype 'n') los nombra Postgres solo.
        assertThat(names("SELECT c.conname FROM pg_constraint c JOIN pg_class t ON t.oid = c.conrelid"
                + " WHERE c.connamespace = 'public'::regnamespace AND c.contype <> 'n'"
                + " AND t.relname <> 'flyway_schema_history'"))
                .isNotEmpty()
                .allMatch(n -> n.matches("(pk|fk|uq|ck)_" + NAME));
        assertThat(names("SELECT indexname FROM pg_indexes WHERE schemaname = 'public'"
                + " AND tablename <> 'flyway_schema_history'"
                + " AND indexname NOT IN (SELECT conname FROM pg_constraint)"))
                .allMatch(n -> n.matches("ix_" + NAME));
    }

    private List<String> names(String sql) {
        return jdbc.queryForList(sql, String.class);
    }
}
