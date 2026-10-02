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
        return "SELECT '" + module.name() + "' AS module, p.id, p.agency_id, a.name AS agency_name, a.invoice_prefix,"
                + " p.invoice_number, p.purchase_date, f.code AS fund_code, p.id_number, p.first_name, p.last_name,"
                + " p.special_type, p.net_kg, p.net_to_pay, p.payment_method, p.status, p.exported, p.annulled_at"
                + " FROM " + module.table + " p JOIN agency a ON a.id = p.agency_id JOIN fund f ON f.id = p.fund_id";
    }

    /** Cns_Compras por numero de factura; cada modulo numera aparte, asi que puede haber mas de una. */
    public List<AnnulmentCandidate> findByInvoice(Long agencyId, Integer invoiceNumber) {
        String sql = Arrays.stream(AnnulmentModule.values())
                .map(m -> select(m) + " WHERE p.invoice_number = :invoice"
                        + " AND (CAST(:agencyId AS BIGINT) IS NULL OR p.agency_id = :agencyId)")
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
        Boolean exits = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM inventory_movement im"
                        + " WHERE im.purchase_module = :module AND im.purchase_id = :id AND (im.remaining_kg < im.net_kg"
                        + " OR EXISTS (SELECT 1 FROM remission_line rl WHERE rl.inventory_movement_id = im.id)))",
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
        int updated = jdbc.update("UPDATE " + module.table + " SET status = 'ANULADA', annulled_at = now(),"
                + " annulled_by_user_id = :userId, check_number = NULL, " + zeros
                + " WHERE id = :id AND status = 'VALIDA' AND NOT exported", params);
        if (updated != 1) {
            throw new IllegalStateException("La compra cambio mientras se anulaba: " + module + " " + id);
        }
        jdbc.update("UPDATE purchase_payment_split SET cash_amount = 0, check_amount = 0, transfer_amount = 0,"
                + " card_amount = 0, check_number = NULL WHERE source = :module AND source_id = :id", params);
        jdbc.update("DELETE FROM inventory_movement WHERE purchase_module = :inventoryModule AND purchase_id = :id", params);
    }

    private static AnnulmentCandidate toCandidate(ResultSet rs) throws SQLException {
        Timestamp annulledAt = rs.getTimestamp("annulled_at");
        return new AnnulmentCandidate(
                AnnulmentModule.valueOf(rs.getString("module")),
                rs.getLong("id"),
                rs.getLong("agency_id"),
                rs.getString("agency_name"),
                rs.getString("invoice_prefix"),
                (Integer) rs.getObject("invoice_number"),
                rs.getObject("purchase_date", LocalDate.class),
                rs.getString("fund_code"),
                rs.getString("id_number"),
                rs.getString("first_name"),
                rs.getString("last_name"),
                rs.getString("special_type"),
                rs.getBigDecimal("net_kg"),
                rs.getBigDecimal("net_to_pay"),
                rs.getString("payment_method"),
                rs.getString("status"),
                rs.getBoolean("exported"),
                annulledAt == null ? null : annulledAt.toInstant());
    }
}
