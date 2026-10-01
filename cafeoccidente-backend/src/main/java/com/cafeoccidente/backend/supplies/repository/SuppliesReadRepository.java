package com.cafeoccidente.backend.supplies.repository;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Solo lectura sobre tablas de otros modulos: los pagos de las compras (lo que Access copiaba a Caja
 * con ActualizaCaja/Ch/Dat/Tx y a Suministros con ActualizaSuministros) y los nombres de Asociados.
 * No escribe nada: el flujo de Compras no se toca.
 */
@Repository
public class SuppliesReadRepository {

    private static final Map<String, String> PURCHASE_TABLES = Map.of(
            "DRY", "dry_coffee_purchase",
            "GREEN", "green_coffee_purchase",
            "HUSK", "husk_purchase",
            "OTHER", "other_coffee_purchase",
            "FERTI", "ferti_futuro_purchase");

    private final NamedParameterJdbcTemplate jdbc;

    public SuppliesReadRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** agencyId null = todas las agencias. */
    public List<PurchasePayment> purchasePayments(Long agencyId, LocalDate from, LocalDate to) {
        String sql = String.join(" UNION ALL ", PURCHASE_TABLES.entrySet().stream()
                .map(e -> "SELECT '" + e.getKey() + "' AS module, p.id, p.invoice_number, p.agency_id,"
                        + " a.name AS agency_name, f.code AS fund_code, p.purchase_date, p.id_number,"
                        // En compras check_number es texto; Caja.Cheque en Access era numerico.
                        + " p.net_to_pay, p.payment_method,"
                        + " CASE WHEN p.check_number ~ '^[0-9]{1,9}$' THEN CAST(p.check_number AS INTEGER) END AS check_number,"
                        + " p.created_at, s.source_id AS split_id, s.cash_amount, s.check_amount,"
                        + " s.transfer_amount, s.card_amount, s.check_number AS split_check_number"
                        + " FROM " + e.getValue() + " p JOIN agency a ON a.id = p.agency_id"
                        + " JOIN fund f ON f.id = p.fund_id"
                        + " LEFT JOIN purchase_payment_split s ON s.source = '" + e.getKey() + "' AND s.source_id = p.id"
                        + " WHERE (CAST(:agencyId AS BIGINT) IS NULL OR p.agency_id = :agencyId)"
                        + " AND p.purchase_date BETWEEN :from AND :to")
                .toList());
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("agencyId", agencyId)
                .addValue("from", from)
                .addValue("to", to);
        List<List<PurchasePayment>> rows = jdbc.query(sql, params, (rs, i) -> {
            Timestamp createdAt = rs.getTimestamp("created_at");
            PurchasePayment payment = new PurchasePayment(
                    rs.getString("module"),
                    rs.getLong("id"),
                    rs.getInt("invoice_number"),
                    rs.getLong("agency_id"),
                    rs.getString("agency_name"),
                    rs.getString("fund_code"),
                    rs.getObject("purchase_date", LocalDate.class),
                    rs.getString("id_number"),
                    rs.getBigDecimal("net_to_pay"),
                    rs.getString("payment_method"),
                    (Integer) rs.getObject("check_number"),
                    createdAt == null ? null : createdAt.toInstant());
            // Pago mixto migrado de Access: una fila por forma de pago en vez del neto bajo una sola.
            return rs.getObject("split_id") == null ? List.of(payment)
                    : payment.splitInto(rs.getBigDecimal("cash_amount"), rs.getBigDecimal("check_amount"),
                            rs.getBigDecimal("transfer_amount"), rs.getBigDecimal("card_amount"),
                            (Integer) rs.getObject("split_check_number"));
        });
        return rows.stream().flatMap(List::stream).toList();
    }

    /** Relacion cheques caja: [1er nombre] & " " & [2o nombre], [1er apellido] & " " & [2o apellido]. */
    public List<GrowerName> growerNames(Collection<String> idNumbers) {
        if (idNumbers.isEmpty()) {
            return List.of();
        }
        return jdbc.query(
                "SELECT id_number, TRIM(CONCAT(first_name, ' ', second_name)) AS first_names,"
                        + " TRIM(CONCAT(last_name, ' ', second_last_name)) AS last_names"
                        + " FROM grower WHERE id_number IN (:ids)",
                new MapSqlParameterSource("ids", idNumbers),
                (rs, i) -> new GrowerName(rs.getString("id_number"), rs.getString("first_names"), rs.getString("last_names")));
    }
}
