package com.cafeoccidente.backend.tables.repository;

import com.cafeoccidente.backend.tables.dto.TableInfo;
import com.cafeoccidente.backend.tables.dto.TableInfo.ColumnInfo;
import com.cafeoccidente.backend.tables.dto.TablePage;
import com.cafeoccidente.backend.tables.dto.TableQuery;
import com.cafeoccidente.backend.tables.dto.TableQuery.Filter;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Solo lectura (RN-03.3). Los nombres de tabla y columna vienen del {@link TableCatalog} (lista blanca) y van entre
 * comillas dobles; todo valor que manda el cliente va como parametro. Nada del request se pega en el SQL.
 */
@Repository
public class TableAccessRepository {

    /** Una busqueda pesada no frena la base: el SELECT se corta a los 5 s. */
    private static final String TIMEOUT = "SET LOCAL statement_timeout = '5s'";

    private final NamedParameterJdbcTemplate jdbc;

    public TableAccessRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public TablePage rows(TableInfo table, TableQuery query) {
        jdbc.getJdbcTemplate().execute(TIMEOUT);
        MapSqlParameterSource params = new MapSqlParameterSource();
        String where = where(table, query, params);
        String from = " FROM " + quote(table.name()) + where;

        Long total = jdbc.queryForObject("SELECT count(*)" + from, params, Long.class);
        String select = table.columns().stream().map(c -> quote(c.name())).collect(Collectors.joining(", "));
        params.addValue("limit", query.limit()).addValue("offset", query.offset());
        List<List<String>> rows = jdbc.query("SELECT " + select + from + orderBy(table, query) + " LIMIT :limit OFFSET :offset",
                params, (rs, i) -> row(rs, table.columns().size()));
        return new TablePage(table.columns().stream().map(ColumnInfo::name).toList(), rows, total == null ? 0 : total);
    }

    private static String where(TableInfo table, TableQuery query, MapSqlParameterSource params) {
        List<String> conditions = new ArrayList<>();
        if (table.byAgency() && query.agencyId() != null) {
            conditions.add("agencia_id = :agencyId");
            params.addValue("agencyId", query.agencyId());
        }
        if (query.search() != null) {
            params.addValue("search", like(query.search()));
            conditions.add(table.columns().stream().map(c -> "CAST(" + quote(c.name()) + " AS text) ILIKE :search ESCAPE '\\'")
                    .collect(Collectors.joining(" OR ", "(", ")")));
        }
        for (int i = 0; i < query.filters().size(); i++) {
            Filter filter = query.filters().get(i);
            String name = "f" + i;
            String column = quote(filter.column().name());
            switch (filter.operator()) {
                case CONTAINS -> {
                    conditions.add("CAST(" + column + " AS text) ILIKE :" + name + " ESCAPE '\\'");
                    params.addValue(name, like((String) filter.value()));
                }
                case FROM -> {
                    conditions.add(typed(filter, column) + " >= :" + name);
                    params.addValue(name, filter.value());
                }
                case TO -> {
                    conditions.add(typed(filter, column) + " <= :" + name);
                    params.addValue(name, filter.value());
                }
            }
        }
        return conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
    }

    /** Las fechas con hora se comparan por el dia. */
    private static String typed(Filter filter, String column) {
        return filter.column().kind() == TableInfo.Kind.DATE ? "CAST(" + column + " AS date)" : column;
    }

    private static String orderBy(TableInfo table, TableQuery query) {
        String key = quote(table.primaryKey()) + " DESC";
        if (query.sort() == null) {
            return " ORDER BY " + key;
        }
        String sort = quote(query.sort()) + (query.ascending() ? " ASC NULLS FIRST" : " DESC NULLS LAST");
        return " ORDER BY " + sort + (query.sort().equals(table.primaryKey()) ? "" : ", " + key);
    }

    /** "contiene", con %, _ y \ del texto tomados como letras. */
    private static String like(String text) {
        return "%" + text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    /** Solo para nombres que ya estan en el catalogo. */
    private static String quote(String identifier) {
        return '"' + identifier.replace("\"", "\"\"") + '"';
    }

    private static List<String> row(ResultSet rs, int columns) throws SQLException {
        List<String> row = new ArrayList<>(columns);
        for (int i = 1; i <= columns; i++) {
            row.add(text(rs.getObject(i)));
        }
        return row;
    }

    /** Como se ve en la hoja de datos: fechas sin hora, fecha y hora sin fracciones, numeros sin notacion cientifica. */
    private static String text(Object value) {
        return switch (value) {
            case null -> null;
            case Date date -> date.toLocalDate().toString();
            case Timestamp timestamp -> timestamp.toLocalDateTime().withNano(0).toString().replace('T', ' ');
            case BigDecimal number -> number.toPlainString();
            default -> value.toString();
        };
    }
}
