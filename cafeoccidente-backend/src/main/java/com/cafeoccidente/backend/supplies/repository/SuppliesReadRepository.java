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
            "HUSK", "compras_pasilla");

    private final NamedParameterJdbcTemplate jdbc;

    public SuppliesReadRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** agencyId null = todas las agencias. */
    public List<PurchasePayment> purchasePayments(Long agencyId, LocalDate from, LocalDate to) {
        String sql = String.join(" UNION ALL ", PURCHASE_TABLES.entrySet().stream()
                .map(e -> "SELECT '" + e.getKey() + "' AS module, p.id, p.numero_factura, p.agencia_id,"
                        + " a.nombre AS agency_name, f.codigo AS fund_code, p.fecha_compra, p.cedula,"
                        // El cheque es texto de 4 digitos con sus ceros (V21, V25): no se pasa a numero.
                        + " p.neto_a_pagar, p.forma_pago, p.numero_cheque,"
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
                    rs.getString("numero_cheque"),
                    createdAt == null ? null : createdAt.toInstant());
            // Pago mixto migrado de Access: una fila por forma de pago en vez del neto bajo una sola.
            return rs.getObject("split_id") == null ? List.of(payment)
                    : payment.splitInto(rs.getBigDecimal("efectivo"), rs.getBigDecimal("cheque"),
                            rs.getBigDecimal("transferencia"), rs.getBigDecimal("datafono"),
                            rs.getString("split_check_number"));
        });
        return rows.stream().flatMap(List::stream).toList();
    }

    /**
     * 6B (11-nov): lo pagado con datafono para el informe de Caja, leido de la cuenta por cobrar. Solo las
     * PENDIENTE: al anular la compra pasa a ANULADA y sale del informe. Fondo, fecha y orden son los de la compra.
     */
    public List<PurchasePayment> cardReceivables(Long agencyId, LocalDate from, LocalDate to) {
        String sql = String.join(" UNION ALL ", PURCHASE_TABLES.entrySet().stream()
                .map(e -> "SELECT c.modulo_compra, p.id, c.numero_factura, c.agencia_id, a.nombre AS agency_name,"
                        + " f.codigo AS fund_code, p.fecha_compra, c.cedula_vendedor, c.valor, p.creado_en"
                        + " FROM cuentas_por_cobrar c JOIN " + e.getValue() + " p ON p.id = c.compra_id"
                        + " JOIN agencias a ON a.id = c.agencia_id JOIN fondos f ON f.id = p.fondo_id"
                        + " WHERE c.modulo_compra = '" + e.getKey() + "' AND c.estado = 'PENDIENTE'"
                        + " AND (CAST(:agencyId AS BIGINT) IS NULL OR c.agencia_id = :agencyId)"
                        + " AND p.fecha_compra BETWEEN :from AND :to")
                .toList());
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("agencyId", agencyId)
                .addValue("from", from)
                .addValue("to", to);
        return jdbc.query(sql, params, (rs, i) -> {
            Timestamp createdAt = rs.getTimestamp("creado_en");
            return new PurchasePayment(rs.getString("modulo_compra"), rs.getLong("id"), rs.getInt("numero_factura"),
                    rs.getLong("agencia_id"), rs.getString("agency_name"), rs.getString("fund_code"),
                    rs.getObject("fecha_compra", LocalDate.class), rs.getString("cedula_vendedor"),
                    rs.getBigDecimal("valor"), "DATAFONO", null, createdAt == null ? null : createdAt.toInstant());
        });
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
