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
            "DRY", "compras_cafe_seco",
            "GREEN", "compras_cafe_verde",
            "HUSK", "compras_pasilla",
            "OTHER", "compras_cafes_otros",
            "FERTI", "compras_ferti_futuro");

    private final NamedParameterJdbcTemplate jdbc;

    public SuppliesReadRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** agencyId null = todas las agencias. */
    public List<PurchasePayment> purchasePayments(Long agencyId, LocalDate from, LocalDate to) {
        String sql = String.join(" UNION ALL ", PURCHASE_TABLES.entrySet().stream()
                .map(e -> "SELECT '" + e.getKey() + "' AS module, p.id, p.numero_factura, p.agencia_id,"
                        + " a.nombre AS agency_name, f.codigo AS fund_code, p.fecha_compra, p.cedula,"
                        // En compras check_number es texto; Caja.Cheque en Access era numerico.
                        + " p.neto_a_pagar, p.forma_pago,"
                        + " CASE WHEN p.numero_cheque ~ '^[0-9]{1,9}$' THEN CAST(p.numero_cheque AS INTEGER) END AS numero_cheque,"
                        + " p.creado_en, s.origen_id AS split_id, s.efectivo, s.cheque,"
                        + " s.transferencia, s.datafono, s.numero_cheque AS split_check_number"
                        + " FROM " + e.getValue() + " p JOIN agencias a ON a.id = p.agencia_id"
                        + " JOIN fondos f ON f.id = p.fondo_id"
                        + " LEFT JOIN formas_pago_compras s ON s.origen = '" + e.getKey() + "' AND s.origen_id = p.id"
                        + " WHERE (CAST(:agencyId AS BIGINT) IS NULL OR p.agencia_id = :agencyId)"
                        + " AND p.fecha_compra BETWEEN :from AND :to")
                .toList());
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("agencyId", agencyId)
                .addValue("from", from)
                .addValue("to", to);
        List<List<PurchasePayment>> rows = jdbc.query(sql, params, (rs, i) -> {
            Timestamp createdAt = rs.getTimestamp("creado_en");
            PurchasePayment payment = new PurchasePayment(
                    rs.getString("module"),
                    rs.getLong("id"),
                    rs.getInt("numero_factura"),
                    rs.getLong("agencia_id"),
                    rs.getString("agency_name"),
                    rs.getString("fund_code"),
                    rs.getObject("fecha_compra", LocalDate.class),
                    rs.getString("cedula"),
                    rs.getBigDecimal("neto_a_pagar"),
                    rs.getString("forma_pago"),
                    (Integer) rs.getObject("numero_cheque"),
                    createdAt == null ? null : createdAt.toInstant());
            // Pago mixto migrado de Access: una fila por forma de pago en vez del neto bajo una sola.
            return rs.getObject("split_id") == null ? List.of(payment)
                    : payment.splitInto(rs.getBigDecimal("efectivo"), rs.getBigDecimal("cheque"),
                            rs.getBigDecimal("transferencia"), rs.getBigDecimal("datafono"),
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
                "SELECT identificacion, TRIM(CONCAT(primer_nombre, ' ', segundo_nombre)) AS first_names,"
                        + " TRIM(CONCAT(primer_apellido, ' ', segundo_apellido)) AS last_names"
                        + " FROM terceros WHERE identificacion IN (:ids)",
                new MapSqlParameterSource("ids", idNumbers),
                (rs, i) -> new GrowerName(rs.getString("identificacion"), rs.getString("first_names"), rs.getString("last_names")));
    }
}
