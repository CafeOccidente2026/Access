package com.cafeoccidente.backend.supplies.repository;

import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Tabla consecutivos_transaccion (V21): el ultimo consecutivo del Id de transaccion por agencia y codigo de pantalla. */
@Repository
public class TransactionIdRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public TransactionIdRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Sube el consecutivo y devuelve el numero tomado. Una sola sentencia: crea la fila o la bloquea hasta el fin de la
     * transaccion, asi dos guardados a la vez no toman el mismo numero. MANDATORY: fuera de la transaccion del
     * guardado el numero se consumiria aunque el guardado fallara.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int take(Long agencyId, String code) {
        Integer taken = jdbc.queryForObject("""
                INSERT INTO consecutivos_transaccion (agencia_id, codigo, ultimo_consecutivo) VALUES (:agencyId, :code, 1)
                ON CONFLICT (agencia_id, codigo) DO UPDATE
                    SET ultimo_consecutivo = consecutivos_transaccion.ultimo_consecutivo + 1
                RETURNING ultimo_consecutivo""", params(agencyId, code), Integer.class);
        return taken == null ? 1 : taken;
    }

    /** El numero que tomaria {@link #take} ahora, sin bloquear ni subir nada (para mostrarlo en pantalla). */
    public int peek(Long agencyId, String code) {
        List<Integer> last = jdbc.queryForList("SELECT ultimo_consecutivo FROM consecutivos_transaccion"
                + " WHERE agencia_id = :agencyId AND codigo = :code", params(agencyId, code), Integer.class);
        return last.isEmpty() ? 1 : last.get(0) + 1;
    }

    private static MapSqlParameterSource params(Long agencyId, String code) {
        return new MapSqlParameterSource().addValue("agencyId", agencyId).addValue("code", code);
    }
}
