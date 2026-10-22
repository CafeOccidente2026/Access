package com.cafeoccidente.backend.purchases.annulment.repository;

import com.cafeoccidente.backend.purchases.annulment.dto.AnnulmentCandidate;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Lecturas y escrituras de "Anular Documento" sobre las 5 tablas de compra (SQL por tabla de AnnulmentModule). */
@Repository
public class PurchaseAnnulmentRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public PurchaseAnnulmentRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static String select(AnnulmentModule module) {
        return "SELECT '" + module.name() + "' AS module, p.id, p.agencia_id, a.nombre AS agency_name, a.prefijo_factura,"
                + " p.numero_factura, p.fecha_compra, f.codigo AS fund_code, p.cedula, p.nombres, p.apellidos,"
                + " p.especial, p.kilos_netos, p.neto_a_pagar, p.forma_pago, p.estado, p.anulacion_bloqueada, p.anulado_en"
                + " FROM " + module.table + " p JOIN agencias a ON a.id = p.agencia_id JOIN fondos f ON f.id = p.fondo_id";
    }

    /** Cns_Compras por numero de factura; cada modulo numera aparte, asi que puede haber mas de una. */
    public List<AnnulmentCandidate> findByInvoice(Long agencyId, Integer invoiceNumber) {
        String sql = Arrays.stream(AnnulmentModule.values())
                .map(m -> select(m) + " WHERE p.numero_factura = :invoice"
                        + " AND (CAST(:agencyId AS BIGINT) IS NULL OR p.agencia_id = :agencyId)")
                .collect(Collectors.joining(" UNION ALL ")) + " ORDER BY agency_name, module";
        return jdbc.query(sql, new MapSqlParameterSource().addValue("invoice", invoiceNumber).addValue("agencyId", agencyId),
                (rs, i) -> toCandidate(rs));
    }

    /** La compra bloqueada hasta el fin de la transaccion: nadie la exporta ni la anula a la vez. */
    public Optional<AnnulmentCandidate> lock(AnnulmentModule module, Long id) {
        List<AnnulmentCandidate> rows = jdbc.query(select(module) + " WHERE p.id = :id FOR UPDATE OF p",
                new MapSqlParameterSource("id", id), (rs, i) -> toCandidate(rs));
        return rows.stream().findFirst();
    }

    /** Entradas de inventario de la compra que ya tuvieron salidas (remision) o saldo menor a lo comprado. */
    public boolean hasInventoryExits(AnnulmentModule module, Long id) {
        Boolean exits = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM inventarios im"
                        + " WHERE im.modulo_compra = :module AND im.compra_id = :id AND (im.saldo_kilos < im.kilos_netos"
                        + " OR EXISTS (SELECT 1 FROM remisiones_detalle rl WHERE rl.inventario_id = im.id)))",
                new MapSqlParameterSource().addValue("module", module.inventoryModule).addValue("id", id), Boolean.class);
        return Boolean.TRUE.equals(exits);
    }

    /**
     * AnularFactura: Forma_de_Pago = "ANULADA", los 29 campos en 0, y "elimina inventario" / "elimina
     * caja" / "elimina suministros". Caja y Suministros se arman al leer de las compras, asi que con el
     * neto y el desglose en 0 ya no aparecen; la entrada de inventario si es una copia y se borra.
     */
    public void annul(AnnulmentModule module, Long id, Long userId) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("id", id).addValue("userId", userId)
                .addValue("module", module.name()).addValue("inventoryModule", module.inventoryModule);
        String zeros = module.zeroed.stream().map(c -> c + " = 0").collect(Collectors.joining(", "));
        int updated = jdbc.update("UPDATE " + module.table + " SET estado = 'ANULADA', anulado_en = now(),"
                + " anulado_por_usuario_id = :userId, numero_cheque = NULL, " + zeros
                + " WHERE id = :id AND estado = 'VALIDA' AND NOT anulacion_bloqueada", params);
        if (updated != 1) {
            throw new IllegalStateException("La compra cambio mientras se anulaba: " + module + " " + id);
        }
        jdbc.update("UPDATE formas_pago_compras SET efectivo = 0, cheque = 0, transferencia = 0,"
                + " datafono = 0, numero_cheque = NULL WHERE origen = :module AND origen_id = :id", params);
        jdbc.update("DELETE FROM inventarios WHERE modulo_compra = :inventoryModule AND compra_id = :id", params);
    }

    private static AnnulmentCandidate toCandidate(ResultSet rs) throws SQLException {
        Timestamp annulledAt = rs.getTimestamp("anulado_en");
        return new AnnulmentCandidate(
                AnnulmentModule.valueOf(rs.getString("module")),
                rs.getLong("id"),
                rs.getLong("agencia_id"),
                rs.getString("agency_name"),
                rs.getString("prefijo_factura"),
                (Integer) rs.getObject("numero_factura"),
                rs.getObject("fecha_compra", LocalDate.class),
                rs.getString("fund_code"),
                rs.getString("cedula"),
                rs.getString("nombres"),
                rs.getString("apellidos"),
                rs.getString("especial"),
                rs.getBigDecimal("kilos_netos"),
                rs.getBigDecimal("neto_a_pagar"),
                rs.getString("forma_pago"),
                rs.getString("estado"),
                rs.getBoolean("anulacion_bloqueada"),
                annulledAt == null ? null : annulledAt.toInstant());
    }
}
