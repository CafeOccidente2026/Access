package com.cafeoccidente.backend.purchases.shared.repository;

import com.cafeoccidente.backend.purchases.shared.dto.DailyGrowerTotals;
import java.time.LocalDate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Acumulado diario de la retencion (Q10): seco + verde + pasilla de la cedula en el dia, en todas
 * las agencias. Las anuladas no suman porque la anulacion deja su Vr. Bruto y Retefuente en 0.
 */
@Repository
public class GrowerDailyTotalsRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public GrowerDailyTotalsRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public DailyGrowerTotals sum(String idNumber, LocalDate purchaseDate) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(vr_bruto), 0) AS vr_bruto, COALESCE(SUM(retefuente), 0) AS retefuente
                FROM (SELECT vr_bruto, retefuente FROM compras_cafe_seco WHERE cedula = :idNumber AND fecha_compra = :day
                      UNION ALL
                      SELECT vr_bruto, retefuente FROM compras_cafe_verde WHERE cedula = :idNumber AND fecha_compra = :day
                      UNION ALL
                      SELECT vr_bruto, retefuente FROM compras_pasilla WHERE cedula = :idNumber AND fecha_compra = :day) compras
                """, new MapSqlParameterSource().addValue("idNumber", idNumber).addValue("day", purchaseDate),
                (rs, i) -> new DailyGrowerTotals(rs.getBigDecimal("vr_bruto"), rs.getBigDecimal("retefuente")));
    }

    /**
     * Bloquea la fila del tercero hasta el fin de la transaccion del guardado: dos compras
     * simultaneas del mismo fiel se hacen una despues de la otra, y la segunda ve el acumulado de la
     * primera.
     */
    public void lockGrower(String idNumber) {
        jdbc.queryForList("SELECT id FROM terceros WHERE identificacion = :idNumber FOR UPDATE",
                new MapSqlParameterSource("idNumber", idNumber), Long.class);
    }
}
