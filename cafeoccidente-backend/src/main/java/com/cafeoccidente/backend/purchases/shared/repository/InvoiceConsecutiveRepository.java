package com.cafeoccidente.backend.purchases.shared.repository;

import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Tabla consecutivos_factura: el ultimo numero de factura emitido por cada punto (Q7). */
@Repository
public class InvoiceConsecutiveRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public InvoiceConsecutiveRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Sube el consecutivo y devuelve el numero tomado. Una sola sentencia: crea la fila o la bloquea
     * hasta el fin de la transaccion, asi dos guardados a la vez no toman el mismo numero. GREATEST
     * salta al inicio del rango cuando cambia la resolucion DIAN.
     */
    public int take(Long agencyId, int resolutionFrom) {
        Integer taken = jdbc.queryForObject("""
                INSERT INTO consecutivos_factura (agencia_id, ultimo_numero) VALUES (:agencyId, :from)
                ON CONFLICT (agencia_id) DO UPDATE
                    SET ultimo_numero = GREATEST(consecutivos_factura.ultimo_numero + 1, :from)
                RETURNING ultimo_numero""", params(agencyId, resolutionFrom), Integer.class);
        return taken == null ? resolutionFrom : taken;
    }

    /** El numero que tomaria {@link #take} ahora, sin bloquear ni subir nada. */
    public int peek(Long agencyId, int resolutionFrom) {
        List<Integer> last = jdbc.queryForList(
                "SELECT ultimo_numero FROM consecutivos_factura WHERE agencia_id = :agencyId",
                params(agencyId, resolutionFrom), Integer.class);
        return last.isEmpty() ? resolutionFrom : Math.max(last.get(0) + 1, resolutionFrom);
    }

    private static MapSqlParameterSource params(Long agencyId, int resolutionFrom) {
        return new MapSqlParameterSource().addValue("agencyId", agencyId).addValue("from", resolutionFrom);
    }
}
