package com.cafeoccidente.backend.inventory.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Demo del 23-nov (V31): un consecutivo por agencia, el de la remision y su remesa (PREFIJO-CONSECUTIVO).
 */
@Repository
public class RemissionConsecutiveRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public RemissionConsecutiveRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Sube el consecutivo y devuelve el numero tomado. Una sola sentencia: crea la fila o la bloquea hasta
     * el fin de la transaccion, asi dos guardados a la vez no toman el mismo numero. MANDATORY: fuera de la
     * transaccion del guardado el numero se consumiria aunque el guardado fallara.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int take(Long agencyId) {
        Integer taken = jdbc.queryForObject("""
                INSERT INTO consecutivos_remision_agencia (agencia_id, ultimo_consecutivo) VALUES (:agencyId, 1)
                ON CONFLICT (agencia_id) DO UPDATE
                    SET ultimo_consecutivo = consecutivos_remision_agencia.ultimo_consecutivo + 1
                RETURNING ultimo_consecutivo""",
                new MapSqlParameterSource().addValue("agencyId", agencyId), Integer.class);
        return taken == null ? 1 : taken;
    }
}
