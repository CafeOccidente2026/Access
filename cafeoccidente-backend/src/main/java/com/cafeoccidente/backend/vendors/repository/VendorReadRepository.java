package com.cafeoccidente.backend.vendors.repository;

import com.cafeoccidente.backend.vendors.dto.BeneficiaryRow;
import com.cafeoccidente.backend.vendors.dto.NessQuotaBalanceRow;
import com.cafeoccidente.backend.vendors.dto.NessQuotaRow;
import java.time.LocalDate;
import java.util.List;
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

    /** COMPRAS como union de las 5 tablas. Kilos_Verdes solo existe en Cafe Verde; Descuento_Fro es la
     *  merma en Verde y Pasilla; PorcAlmSana en Pasilla es el porcentaje de almendra y Verde no lo usa. */
    private static final String PURCHASES = String.join(" UNION ALL ",
            purchase("dry_coffee_purchase", "0", "p.healthy_percentage", "p.freight_discount"),
            purchase("other_coffee_purchase", "0", "p.healthy_percentage", "p.freight_discount"),
            purchase("ferti_futuro_purchase", "0", "p.healthy_percentage", "p.freight_discount"),
            purchase("green_coffee_purchase", "p.green_kg", "0", "p.shrinkage_discount"),
            purchase("husk_purchase", "0", "p.almond_percentage", "p.shrinkage_discount"));

    /** Cedula de la tabla NESS: el CSV de origen la trae como numero ("210514.0"). */
    private static final String NESS_ID = "regexp_replace(n.cedula, '\\.0$', '')";

    private final NamedParameterJdbcTemplate jdbc;

    public VendorReadRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static String purchase(String table, String greenKg, String healthyPercentage, String freightDiscount) {
        return "SELECT p.agency_id, a.name AS agency_name, p.purchase_date, f.code AS fund_code, p.invoice_number,"
                + " p.id_number, " + greenKg + " AS green_kg, p.net_kg, " + healthyPercentage + " AS healthy_percentage,"
                + " p.gross_value, p.associate_contribution, p.cooperative_discount, " + freightDiscount
                + " AS freight_discount, p.withholding, p.other_discounts, p.net_to_pay, p.special_type"
                + " FROM " + table + " p JOIN agency a ON a.id = p.agency_id JOIN fund f ON f.id = p.fund_id";
    }

    /** "Beneficiario" (sin fechas) y "Beneficiario Resumen": Cedula Like texto & "*"; agencyId null = todas. */
    public List<BeneficiaryRow> beneficiary(Long agencyId, String idNumberPrefix, LocalDate from, LocalDate to) {
        String sql = "SELECT c.*, g.first_name, g.last_name FROM (" + PURCHASES + ") c"
                + " JOIN grower g ON g.id_number = c.id_number"
                + " WHERE (CAST(:agencyId AS BIGINT) IS NULL OR c.agency_id = :agencyId)"
                + " AND c.id_number LIKE :prefix ESCAPE '\\'"
                + " AND (CAST(:from AS DATE) IS NULL OR c.purchase_date BETWEEN :from AND :to)"
                + " ORDER BY c.id_number, c.invoice_number";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("agencyId", agencyId)
                .addValue("prefix", likePrefix(idNumberPrefix))
                .addValue("from", from)
                .addValue("to", to);
        return jdbc.query(sql, params, (rs, i) -> new BeneficiaryRow(
                rs.getString("agency_name"),
                rs.getObject("purchase_date", LocalDate.class),
                rs.getString("fund_code"),
                (Integer) rs.getObject("invoice_number"),
                rs.getString("id_number"),
                rs.getString("first_name"),
                rs.getString("last_name"),
                rs.getBigDecimal("green_kg"),
                rs.getBigDecimal("net_kg"),
                rs.getBigDecimal("healthy_percentage"),
                rs.getBigDecimal("gross_value"),
                rs.getBigDecimal("associate_contribution"),
                rs.getBigDecimal("cooperative_discount"),
                rs.getBigDecimal("freight_discount"),
                rs.getBigDecimal("withholding"),
                rs.getBigDecimal("other_discounts"),
                rs.getBigDecimal("net_to_pay"),
                rs.getString("special_type")));
    }

    /** "CuposNess": la tabla NESS en su orden de carga, filtrada por prefijo de cedula. */
    public List<NessQuotaRow> nessQuotas(String idNumberPrefix, int offset, int limit) {
        return jdbc.query(
                "SELECT " + NESS_ID + " AS cedula, n.nombres, n.programa, CAST(n.cupo AS NUMERIC) AS cupo"
                        + " FROM staging_legacy_ness n WHERE " + NESS_ID + " LIKE :prefix ESCAPE '\\'"
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
                "SELECT COUNT(*) FROM staging_legacy_ness n WHERE " + NESS_ID + " LIKE :prefix ESCAPE '\\'",
                new MapSqlParameterSource("prefix", likePrefix(idNumberPrefix)), Long.class);
        return total == null ? 0 : total;
    }

    /**
     * "CalCuposSaldoNess" + "NESSYRAINSALDO": CUPOS = compras con Especial exactamente igual a uno de los
     * 4 programas; NESS INNER JOIN CUPOS solo por cedula (no por programa), agrupado por cedula, nombres,
     * programa y cupo - tal cual la consulta de Access.
     */
    public List<NessQuotaBalanceRow> nessQuotaBalances(Long agencyId) {
        String sql = "WITH cupos AS (SELECT c.id_number AS cedula, c.net_kg AS kilos FROM (" + PURCHASES + ") c"
                + " WHERE c.special_type IN (:specials)"
                + " AND (CAST(:agencyId AS BIGINT) IS NULL OR c.agency_id = :agencyId))"
                + " SELECT cu.cedula, n.nombres, n.programa, CAST(n.cupo AS NUMERIC) AS cupo, SUM(cu.kilos) AS facturados"
                + " FROM staging_legacy_ness n JOIN cupos cu ON " + NESS_ID + " = cu.cedula"
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
