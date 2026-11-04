package com.cafeoccidente.backend.inventory.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * RN-11.5: consecutivo PREFIJO-FONDO-CONSECUTIVO por agencia y fondo, compartido por las Salidas de
 * inventario y las remesas, asi un numero nunca se repite entre los dos procesos.
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
    public int take(Long agencyId, Long fundId) {
        Integer taken = jdbc.queryForObject("""
                INSERT INTO consecutivos_remision (agencia_id, fondo_id, ultimo_consecutivo) VALUES (:agencyId, :fundId, 1)
                ON CONFLICT (agencia_id, fondo_id) DO UPDATE
                    SET ultimo_consecutivo = consecutivos_remision.ultimo_consecutivo + 1
                RETURNING ultimo_consecutivo""",
                new MapSqlParameterSource().addValue("agencyId", agencyId).addValue("fundId", fundId), Integer.class);
        return taken == null ? 1 : taken;
    }
}
