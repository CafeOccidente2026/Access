package com.cafeoccidente.backend.tables.repository;

import com.cafeoccidente.backend.tables.dto.TablePage;
import com.cafeoccidente.backend.tables.entity.QueryableTable;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Solo lectura (RN-03.3): un SELECT paginado sobre una tabla de la lista blanca, nada mas. */
@Repository
public class TableAccessRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public TableAccessRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public TablePage page(QueryableTable table, Long agencyId, int page, int size) {
        String where = table.isByAgency() ? " WHERE agencia_id = :agencyId" : "";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("agencyId", agencyId).addValue("size", size).addValue("offset", (long) page * size);
        Long total = jdbc.queryForObject("SELECT count(*) FROM " + table.getTableName() + where, params, Long.class);
        long count = total == null ? 0 : total;
        ResultSetExtractor<TablePage> toPage = rs -> toPage(rs, count);
        return jdbc.query("SELECT * FROM " + table.getTableName() + where + " ORDER BY id DESC LIMIT :size OFFSET :offset",
                params, toPage);
    }

    private static TablePage toPage(ResultSet rs, long total) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        List<String> columns = new ArrayList<>();
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            columns.add(meta.getColumnLabel(i));
        }
        List<List<String>> rows = new ArrayList<>();
        while (rs.next()) {
            List<String> row = new ArrayList<>();
            for (int i = 1; i <= columns.size(); i++) {
                row.add(text(rs.getObject(i)));
            }
            rows.add(row);
        }
        return new TablePage(columns, rows, total);
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
