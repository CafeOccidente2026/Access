package com.cafeoccidente.backend.vendors.repository;

import com.cafeoccidente.backend.vendors.dto.BeneficiaryRow;
import com.cafeoccidente.backend.vendors.dto.NessQuotaBalanceRow;
import com.cafeoccidente.backend.vendors.dto.NessQuotaRow;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Solo lectura. En Access todas las pantallas de compra escriben en la tabla COMPRAS; aca son las 5
 * tablas de compras, unidas con los nombres de columna de COMPRAS. No escribe nada.
 *
 * <p>Las facturas ANULADAS (V37) salen en 0, como en Access. Limitacion conocida: Saldos Cupos replica
 * el filtro literal de Access: las variantes de Especial ("NESPRESSO - FTUSA", "CON TAZA", etc.) no coinciden con los 4
 * nombres exactos y no aparecen, igual que en Access hoy.
 */
@Repository
public class VendorReadRepository {

    /** Programas de "CalCuposSaldoNess" (NESSCUPOTODOS, RAINCUPOTODOS, NESSLHCUPOTODOS, RN4CCUPOTODOS):
     *  igualdad exacta con COMPRAS.Especial, como en Access. */
    public static final List<String> QUOTA_SPECIALS =
            List.of("NESPRESSO", "RAINFOREST", "NESPRESSO LATE HARVEST", "REGIONAL NARIÑO 4C");

    /** Las 4 tablas de compra: lista fija de tablas permitidas en purchase(), que las concatena al SQL. */
    private static final Set<String> PURCHASE_TABLES = Set.of(
            "compras_cafe_seco", "compras_ferti_futuro", "compras_cafe_verde", "compras_pasilla");

    /** COMPRAS como union de las 4 tablas. Kilos_Verdes solo existe en Cafe Verde; Descuento_Fro es la
     *  merma en Verde y Pasilla; PorcAlmSana en Pasilla es el porcentaje de almendra y Verde no lo usa. */
    private static final String PURCHASES = String.join(" UNION ALL ",
            purchase("compras_cafe_seco", "0", "p.factor", "p.descuento_financiero"),
            purchase("compras_ferti_futuro", "0", "p.factor", "p.descuento_financiero"),
            purchase("compras_cafe_verde", "p.kilos_verdes", "0", "p.descuento_financiero"),
            purchase("compras_pasilla", "0", "p.porc_almendra", "p.descuento_financiero"));

    /** Cedula de la tabla NESS: el CSV de origen la trae como numero ("210514.0"). */
    private static final String NESS_ID = "regexp_replace(n.cedula, '\\.0$', '')";

    private final NamedParameterJdbcTemplate jdbc;

    public VendorReadRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    static String purchase(String table, String greenKg, String healthyPercentage, String freightDiscount) {
        if (!PURCHASE_TABLES.contains(table)) {
            throw new IllegalArgumentException("Tabla de compras no permitida: " + table);
        }
        return "SELECT p.agencia_id, a.nombre AS agency_name, p.fecha_compra, f.codigo AS fund_code, p.numero_factura,"
                + " p.cedula, " + greenKg + " AS kilos_verdes, p.kilos_netos, " + healthyPercentage + " AS factor,"
                + " p.vr_bruto, p.aporte_asociado, p.descuento_cooperativo, " + freightDiscount
                + " AS descuento_financiero, p.retefuente, p.otros_descuentos, p.neto_a_pagar, p.especial"
                + " FROM " + table + " p JOIN agencias a ON a.id = p.agencia_id JOIN fondos f ON f.id = p.fondo_id";
    }

    /** "Beneficiario" (sin fechas) y "Beneficiario Resumen": Cedula Like texto & "*"; agencyId null = todas. */
    public List<BeneficiaryRow> beneficiary(Long agencyId, String idNumberPrefix, LocalDate from, LocalDate to) {
        String sql = "SELECT c.*, g.primer_nombre, g.primer_apellido FROM (" + PURCHASES + ") c"
                + " JOIN terceros g ON g.identificacion = c.cedula"
                + " WHERE (CAST(:agencyId AS BIGINT) IS NULL OR c.agencia_id = :agencyId)"
                + " AND c.cedula LIKE :prefix ESCAPE '\\'"
                + " AND (CAST(:from AS DATE) IS NULL OR c.fecha_compra BETWEEN :from AND :to)"
                + " ORDER BY c.cedula, c.numero_factura";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("agencyId", agencyId)
                .addValue("prefix", likePrefix(idNumberPrefix))
                .addValue("from", from)
                .addValue("to", to);
        return jdbc.query(sql, params, (rs, i) -> new BeneficiaryRow(
                rs.getString("agency_name"),
                rs.getObject("fecha_compra", LocalDate.class),
                rs.getString("fund_code"),
                (Integer) rs.getObject("numero_factura"),
                rs.getString("cedula"),
                rs.getString("primer_nombre"),
                rs.getString("primer_apellido"),
                rs.getBigDecimal("kilos_verdes"),
                rs.getBigDecimal("kilos_netos"),
                rs.getBigDecimal("factor"),
                rs.getBigDecimal("vr_bruto"),
                rs.getBigDecimal("aporte_asociado"),
                rs.getBigDecimal("descuento_cooperativo"),
                rs.getBigDecimal("descuento_financiero"),
                rs.getBigDecimal("retefuente"),
                rs.getBigDecimal("otros_descuentos"),
                rs.getBigDecimal("neto_a_pagar"),
                rs.getString("especial")));
    }

    /** "CuposNess": la tabla NESS en su orden de carga, filtrada por prefijo de cedula. */
    public List<NessQuotaRow> nessQuotas(String idNumberPrefix, int offset, int limit) {
        return jdbc.query(
                "SELECT " + NESS_ID + " AS cedula, n.nombres, n.programa, CAST(n.cupo AS NUMERIC) AS cupo"
                        + " FROM cupos_ness n WHERE " + NESS_ID + " LIKE :prefix ESCAPE '\\'"
                        + " ORDER BY n.id OFFSET :offset LIMIT :limit",
                new MapSqlParameterSource()
                        .addValue("prefix", likePrefix(idNumberPrefix))
                        .addValue("offset", offset)
                        .addValue("limit", limit),
                (rs, i) -> new NessQuotaRow(
                        rs.getString("cedula"), rs.getString("nombres"), rs.getString("programa"), rs.getBigDecimal("cupo")));
    }

    public long countNessQuotas(String idNumberPrefix) {
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM cupos_ness n WHERE " + NESS_ID + " LIKE :prefix ESCAPE '\\'",
                new MapSqlParameterSource("prefix", likePrefix(idNumberPrefix)), Long.class);
        return total == null ? 0 : total;
    }

    /**
     * "CalCuposSaldoNess" + "NESSYRAINSALDO": CUPOS = compras con Especial exactamente igual a uno de los
     * 4 programas; NESS INNER JOIN CUPOS solo por cedula (no por programa), agrupado por cedula, nombres,
     * programa y cupo - tal cual la consulta de Access.
     */
    public List<NessQuotaBalanceRow> nessQuotaBalances(Long agencyId) {
        String sql = "WITH cupos AS (SELECT c.cedula AS cedula, c.kilos_netos AS kilos FROM (" + PURCHASES + ") c"
                + " WHERE c.especial IN (:specials)"
                + " AND (CAST(:agencyId AS BIGINT) IS NULL OR c.agencia_id = :agencyId))"
                + " SELECT cu.cedula, n.nombres, n.programa, CAST(n.cupo AS NUMERIC) AS cupo, SUM(cu.kilos) AS facturados"
                + " FROM cupos_ness n JOIN cupos cu ON " + NESS_ID + " = cu.cedula"
                + " GROUP BY cu.cedula, n.nombres, n.programa, n.cupo ORDER BY cu.cedula, n.programa";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("specials", QUOTA_SPECIALS)
                .addValue("agencyId", agencyId);
        return jdbc.query(sql, params, (rs, i) -> new NessQuotaBalanceRow(
                rs.getString("cedula"),
                rs.getString("nombres"),
                rs.getString("programa"),
                rs.getBigDecimal("cupo"),
                rs.getBigDecimal("facturados"),
                rs.getBigDecimal("cupo").subtract(rs.getBigDecimal("facturados"))));
    }

    /** Like texto & "*" de Access: vacio = todos; % y _ se buscan literales. */
    static String likePrefix(String text) {
        String trimmed = text == null ? "" : text.trim();
        return trimmed.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }
}
