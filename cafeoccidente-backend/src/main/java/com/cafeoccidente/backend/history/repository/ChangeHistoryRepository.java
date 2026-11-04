package com.cafeoccidente.backend.history.repository;

import com.cafeoccidente.backend.history.dto.ChangeHistoryEntry;
import com.cafeoccidente.backend.history.dto.ChangeHistoryFilter;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** historial_cambios con JDBC: antes/despues van como jsonb. */
@Repository
public class ChangeHistoryRepository {

    private static final TypeReference<Map<String, String>> FIELDS = new TypeReference<>() { };

    /** Filtros opcionales: un parametro null no filtra. to es exclusivo (el dia siguiente al elegido). */
    private static final String WHERE = " WHERE (CAST(:entity AS VARCHAR) IS NULL OR h.entidad = :entity)"
            + " AND (CAST(:agencyId AS BIGINT) IS NULL OR h.agencia_id = :agencyId)"
            + " AND (CAST(:userId AS BIGINT) IS NULL OR h.usuario_id = :userId)"
            + " AND (CAST(:from AS DATE) IS NULL OR h.fecha >= CAST(:from AS DATE))"
            + " AND (CAST(:to AS DATE) IS NULL OR h.fecha < CAST(:to AS DATE) + 1)";

    private final NamedParameterJdbcTemplate jdbc;
    private final JsonMapper json;

    public ChangeHistoryRepository(NamedParameterJdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public void insert(Long userId, String entity, Long entityId, String action, Long agencyId,
            Map<String, String> before, Map<String, String> after) {
        jdbc.update("INSERT INTO historial_cambios (usuario_id, entidad, entidad_id, accion, agencia_id, antes, despues)"
                        + " VALUES (:userId, :entity, :entityId, :action, :agencyId,"
                        + " CAST(:before AS jsonb), CAST(:after AS jsonb))",
                new MapSqlParameterSource().addValue("userId", userId).addValue("entity", entity)
                        .addValue("entityId", entityId).addValue("action", action).addValue("agencyId", agencyId)
                        .addValue("before", before == null ? null : json.writeValueAsString(before))
                        .addValue("after", after == null ? null : json.writeValueAsString(after)));
    }

    public List<ChangeHistoryEntry> search(ChangeHistoryFilter filter) {
        return jdbc.query("SELECT h.id, h.fecha, u.usuario, h.entidad, h.entidad_id, h.accion, a.nombre AS agencia,"
                        + " h.antes, h.despues FROM historial_cambios h JOIN usuarios u ON u.id = h.usuario_id"
                        + " LEFT JOIN agencias a ON a.id = h.agencia_id" + WHERE
                        + " ORDER BY h.fecha DESC, h.id DESC LIMIT :size OFFSET :offset",
                params(filter).addValue("size", filter.size()).addValue("offset", (long) filter.page() * filter.size()),
                (rs, i) -> toEntry(rs));
    }

    public long count(ChangeHistoryFilter filter) {
        Long total = jdbc.queryForObject("SELECT count(*) FROM historial_cambios h" + WHERE, params(filter), Long.class);
        return total == null ? 0 : total;
    }

    private static MapSqlParameterSource params(ChangeHistoryFilter filter) {
        return new MapSqlParameterSource().addValue("entity", filter.entity()).addValue("agencyId", filter.agencyId())
                .addValue("userId", filter.userId()).addValue("from", filter.from()).addValue("to", filter.to());
    }

    private ChangeHistoryEntry toEntry(ResultSet rs) throws SQLException {
        return new ChangeHistoryEntry(rs.getLong("id"), rs.getObject("fecha", OffsetDateTime.class),
                rs.getString("usuario"), rs.getString("entidad"), rs.getLong("entidad_id"), rs.getString("accion"),
                rs.getString("agencia"), fields(rs.getString("antes")), fields(rs.getString("despues")));
    }

    private Map<String, String> fields(String value) {
        return value == null ? null : json.readValue(value, FIELDS);
    }
}
