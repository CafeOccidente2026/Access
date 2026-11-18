package com.cafeoccidente.backend.tables.repository;

import com.cafeoccidente.backend.tables.dto.TableInfo;
import com.cafeoccidente.backend.tables.dto.TableInfo.ColumnInfo;
import com.cafeoccidente.backend.tables.dto.TableInfo.Kind;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * La lista blanca de Acceso a Tablas, leida de information_schema: solo los nombres de tabla y columna que estan
 * aca pueden llegar al SQL. Se excluye el historial de Flyway y la clave de los usuarios nunca se lee.
 */
@Component
public class TableCatalog {

    private static final Set<String> HIDDEN_TABLES = Set.of("flyway_schema_history");
    /** Columnas que nunca se muestran, como tabla.columna. */
    private static final Set<String> HIDDEN_COLUMNS = Set.of("usuarios.contrasena_hash");
    private static final Set<String> NUMBERS = Set.of("integer", "bigint", "smallint", "numeric", "real", "double precision");

    private final JdbcTemplate jdbc;
    private volatile Map<String, TableInfo> tables;

    public TableCatalog(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<TableInfo> all() {
        return List.copyOf(load().values());
    }

    public Optional<TableInfo> find(String name) {
        return Optional.ofNullable(load().get(name));
    }

    // ponytail: se lee una vez por arranque; si una migracion agrega tablas, se ven tras reiniciar el backend.
    private Map<String, TableInfo> load() {
        Map<String, TableInfo> loaded = tables;
        if (loaded == null) {
            loaded = read();
            tables = loaded;
        }
        return loaded;
    }

    private Map<String, TableInfo> read() {
        Map<String, String> keys = new HashMap<>();
        jdbc.query("""
                SELECT k.table_name, k.column_name
                FROM information_schema.table_constraints t
                JOIN information_schema.key_column_usage k
                  ON k.constraint_name = t.constraint_name AND k.table_schema = t.table_schema
                WHERE t.constraint_type = 'PRIMARY KEY' AND t.table_schema = 'public'
                ORDER BY k.ordinal_position
                """, rs -> {
            keys.putIfAbsent(rs.getString(1), rs.getString(2));
        });
        Map<String, List<ColumnInfo>> columns = new LinkedHashMap<>();
        jdbc.query("""
                SELECT c.table_name, c.column_name, c.data_type
                FROM information_schema.columns c
                JOIN information_schema.tables t ON t.table_name = c.table_name AND t.table_schema = c.table_schema
                WHERE c.table_schema = 'public' AND t.table_type = 'BASE TABLE'
                ORDER BY c.table_name, c.ordinal_position
                """, rs -> {
            String table = rs.getString(1);
            String column = rs.getString(2);
            if (!HIDDEN_TABLES.contains(table) && !HIDDEN_COLUMNS.contains(table + "." + column)) {
                columns.computeIfAbsent(table, t -> new ArrayList<>()).add(new ColumnInfo(column, kind(rs.getString(3))));
            }
        });
        Map<String, TableInfo> result = new LinkedHashMap<>();
        columns.forEach((table, cols) -> {
            boolean byAgency = cols.stream().anyMatch(c -> c.name().equals("agencia_id"));
            String key = keys.getOrDefault(table, cols.get(0).name());
            result.put(table, new TableInfo(table, byAgency, key, List.copyOf(cols)));
        });
        return result;
    }

    private static Kind kind(String dataType) {
        if (NUMBERS.contains(dataType)) {
            return Kind.NUMBER;
        }
        if (dataType.equals("date") || dataType.startsWith("timestamp")) {
            return Kind.DATE;
        }
        return dataType.equals("boolean") ? Kind.BOOLEAN : Kind.TEXT;
    }
}
