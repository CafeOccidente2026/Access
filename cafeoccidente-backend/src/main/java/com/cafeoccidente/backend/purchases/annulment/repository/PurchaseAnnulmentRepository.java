package com.cafeoccidente.backend.purchases.annulment.repository;

import com.cafeoccidente.backend.purchases.annulment.dto.AnnulmentCandidate;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.function.Function;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Lecturas y escrituras de "Anular Documento" sobre las 3 tablas de compra (SQL por tabla de AnnulmentModule). */
@Repository
public class PurchaseAnnulmentRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public PurchaseAnnulmentRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static String select(AnnulmentModule module) {
        return "SELECT '" + module.name() + "' AS module, p.id, p.agencia_id, a.nombre AS agency_name, rc.prefijo AS prefijo_factura,"
                + " p.numero_factura, p.fecha_compra, f.codigo AS fund_code, p.cedula, p.nombres, p.apellidos,"
                + " p.especial, p.kilos_netos, p.neto_a_pagar, p.forma_pago, p.estado, p.anulacion_bloqueada, p.anulado_en"
                + " FROM " + module.table + " p JOIN agencias a ON a.id = p.agencia_id JOIN fondos f ON f.id = p.fondo_id"
                + " LEFT JOIN registros_control rc ON rc.agencia_id = p.agencia_id AND rc.activo";
    }

    private static final String OF_AGENCY = " AND (CAST(:agencyId AS BIGINT) IS NULL OR p.agencia_id = :agencyId)";

    private static String unionOfModules(Function<AnnulmentModule, String> sql) {
        return Arrays.stream(AnnulmentModule.values()).map(sql).collect(Collectors.joining(" UNION ALL "));
    }

    /**
     * Cns_Compras por numero de factura. Seco, verde y pasilla comparten el consecutivo (Q7), pero las
     * compras traidas de Access pueden repetir numero entre modulos, asi que puede haber mas de una.
     */
    public List<AnnulmentCandidate> findByInvoice(Long agencyId, Integer invoiceNumber) {
        String sql = unionOfModules(m -> select(m) + " WHERE p.numero_factura = :invoice" + OF_AGENCY)
                + " ORDER BY agency_name, module";
        return jdbc.query(sql, new MapSqlParameterSource().addValue("invoice", invoiceNumber).addValue("agencyId", agencyId),
                (rs, i) -> toCandidate(rs));
    }

    /** RN-23.2: la factura vecina de "from" en la agencia, en los 3 modulos; vacio si no hay mas. */
    public Optional<Integer> neighborInvoice(Long agencyId, Integer from, AnnulmentDirection direction) {
        String filter = from == null || direction.comparison == null ? ""
                : " AND p.numero_factura " + direction.comparison + " :from";
        String sql = "SELECT " + direction.aggregate + "(numero_factura) FROM ("
                + unionOfModules(m -> "SELECT p.numero_factura FROM " + m.table + " p WHERE TRUE" + OF_AGENCY + filter)
                + ") f";
        return Optional.ofNullable(jdbc.queryForObject(sql,
                new MapSqlParameterSource().addValue("agencyId", agencyId).addValue("from", from), Integer.class));
    }

    /** La compra bloqueada hasta el fin de la transaccion: nadie mas la anula a la vez. */
    public Optional<AnnulmentCandidate> lock(AnnulmentModule module, Long id) {
        List<AnnulmentCandidate> rows = jdbc.query(select(module) + " WHERE p.id = :id FOR UPDATE OF p",
                new MapSqlParameterSource("id", id), (rs, i) -> toCandidate(rs));
        return rows.stream().findFirst();
    }

    /**
     * Entradas de inventario de la compra que ya tuvieron salidas (remision) o saldo menor a lo comprado.
     * Bloquea antes la entrada: una remision que la toque a la vez espera a que termine la anulacion.
     */
    public boolean hasInventoryExits(AnnulmentModule module, Long id) {
        jdbc.queryForList("SELECT id FROM inventarios WHERE modulo_compra = :module AND compra_id = :id FOR UPDATE",
                new MapSqlParameterSource().addValue("module", module.inventoryModule).addValue("id", id));
        Boolean exits = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM inventarios im"
                        + " WHERE im.modulo_compra = :module AND im.compra_id = :id AND (im.saldo_kilos < im.kilos_netos"
                        + " OR EXISTS (SELECT 1 FROM remisiones_detalle rl WHERE rl.inventario_id = im.id)))",
                new MapSqlParameterSource().addValue("module", module.inventoryModule).addValue("id", id), Boolean.class);
        return Boolean.TRUE.equals(exits);
    }

    /** Q35: la cuenta por cobrar del datafono ya no esta PENDIENTE (saldada u otro estado). */
    public boolean hasReceivableNotPending(AnnulmentModule module, Long id) {
        Boolean settled = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM cuentas_por_cobrar WHERE modulo_compra = :module"
                        + " AND compra_id = :id AND estado <> 'PENDIENTE')",
                new MapSqlParameterSource().addValue("module", module.name()).addValue("id", id), Boolean.class);
        return Boolean.TRUE.equals(settled);
    }

    /**
     * AnularFactura: Forma_de_Pago = "ANULADA", los 29 campos en 0, y "elimina inventario" / "elimina
     * caja" / "elimina suministros". Caja y Suministros se arman al leer de las compras, asi que con el
     * neto y el desglose en 0 ya no aparecen; la entrada de inventario si es una copia y se borra.
     * RN-23.6: antes de poner los kilos en 0 los devuelve al saldo de la compra anunciada (el cupo del
     * anuncio se calcula sumando las compras, asi que vuelve solo). El UPDATE bloquea esa fila.
     */
    public void annul(AnnulmentModule module, Long id, Long userId) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("id", id).addValue("userId", userId)
                .addValue("module", module.name()).addValue("inventoryModule", module.inventoryModule);
        if (module == AnnulmentModule.DRY) {
            jdbc.update("UPDATE compras_anunciadas a SET saldo = a.saldo + p.kilos_netos FROM compras_cafe_seco p"
                    + " WHERE p.id = :id AND a.id = p.compra_anunciada_id", params);
        }
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
        // Q33/Q35: la cuenta por cobrar del datafono queda anulada con la compra.
        jdbc.update("UPDATE cuentas_por_cobrar SET estado = 'ANULADA' WHERE modulo_compra = :module AND compra_id = :id"
                + " AND estado = 'PENDIENTE'", params);
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
