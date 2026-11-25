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
        // 25-nov: se salta los numeros que la agencia ya usa (remesas y remisiones de Access, como SA4819 o
        // CO9999), asi la remision nueva nunca choca con una vieja.
        int taken = next(agencyId);
        while (used(agencyId, taken)) {
            taken = next(agencyId);
        }
        return taken;
    }

    private int next(Long agencyId) {
        Integer taken = jdbc.queryForObject("""
                INSERT INTO consecutivos_remision_agencia (agencia_id, ultimo_consecutivo) VALUES (:agencyId, 1)
                ON CONFLICT (agencia_id) DO UPDATE
                    SET ultimo_consecutivo = consecutivos_remision_agencia.ultimo_consecutivo + 1
                RETURNING ultimo_consecutivo""",
                new MapSqlParameterSource().addValue("agencyId", agencyId), Integer.class);
        return taken == null ? 1 : taken;
    }

    /** El numero ya esta en una remision o remesa de la agencia (por su consecutivo o por el numero del texto). */
    private boolean used(Long agencyId, int number) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM remisiones WHERE agencia_id = :agencyId
                                 AND (numero_remision_anterior = :number OR consecutivo = :number))
                    OR EXISTS (SELECT 1 FROM remesas WHERE agencia_id = :agencyId
                                 AND (consecutivo = :number
                                      OR substring(numero_remesa from '([0-9]+)$')::numeric = :number))""",
                new MapSqlParameterSource().addValue("agencyId", agencyId).addValue("number", number), Boolean.class));
    }
}
