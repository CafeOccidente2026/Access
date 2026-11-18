package com.cafeoccidente.backend.purchases.correction.repository;

import com.cafeoccidente.backend.purchases.annulment.repository.AnnulmentModule;
import com.cafeoccidente.backend.purchases.correction.dto.CorrectedPurchase;
import com.cafeoccidente.backend.purchases.shared.dto.PurchasePaymentRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Lo que la correccion (HU-05) lee y escribe con SQL sobre las 3 tablas de compra y sus copias. */
@Repository
public class PurchaseCorrectionRepository {

    /** Columnas del Historial: como quedo la compra antes y despues de corregirla. */
    private static final String TRACE_COLUMNS = "p.sacos, p.kilos_brutos, p.destare, p.kilos_netos, p.vr_bruto,"
            + " p.retefuente, p.neto_a_pagar, p.forma_pago, p.numero_cheque, p.direccion, p.celular,"
            + " s.efectivo, s.cheque, s.transferencia, s.datafono";

    // SQL fijo, armado una sola vez con los datos del enum (S2077): nada del request entra al texto.
    static final Map<AnnulmentModule, String> FIND = byModule(m -> "SELECT agencia_id, fondo_id, especial, numero_anuncio,"
            + " numero_factura, fecha_compra, cedula, nombres, apellidos, tipo_vendedor, direccion, celular,"
            + " exento_retencion, kilos_netos, vr_bruto, retefuente FROM " + m.table() + " WHERE id = :id");
    static final Map<AnnulmentModule, String> TRACE = byModule(m -> "SELECT " + TRACE_COLUMNS + " FROM " + m.table() + " p"
            + " LEFT JOIN formas_pago_compras s ON s.origen = '" + m.name() + "' AND s.origen_id = p.id WHERE p.id = :id");
    static final Map<AnnulmentModule, String> MARK = byModule(m -> "UPDATE " + m.table() + " SET corregida = true,"
            + " corregida_en = now(), corregida_por_usuario_id = :userId WHERE id = :id");

    private final NamedParameterJdbcTemplate jdbc;

    public PurchaseCorrectionRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static Map<AnnulmentModule, String> byModule(Function<AnnulmentModule, String> sql) {
        Map<AnnulmentModule, String> map = new EnumMap<>(AnnulmentModule.class);
        Arrays.stream(AnnulmentModule.values()).forEach(m -> map.put(m, sql.apply(m)));
        return Collections.unmodifiableMap(map);
    }

    public CorrectedPurchase find(AnnulmentModule module, Long id) {
        return jdbc.queryForObject(FIND.get(module), new MapSqlParameterSource("id", id), (rs, i) -> new CorrectedPurchase(
                rs.getLong("agencia_id"), rs.getLong("fondo_id"), rs.getString("especial"), rs.getString("numero_anuncio"),
                rs.getInt("numero_factura"), rs.getObject("fecha_compra", LocalDate.class), rs.getString("cedula"),
                rs.getString("nombres"), rs.getString("apellidos"), rs.getString("tipo_vendedor"),
                rs.getString("direccion"), rs.getString("celular"), rs.getBoolean("exento_retencion"),
                rs.getBigDecimal("kilos_netos"), rs.getBigDecimal("vr_bruto"), rs.getBigDecimal("retefuente")));
    }

    /** Pesos, valores y pago de la compra, por nombre de columna, para el Historial. */
    public Map<String, String> trace(AnnulmentModule module, Long id) {
        return jdbc.queryForObject(TRACE.get(module), new MapSqlParameterSource("id", id), (rs, i) -> {
            Map<String, String> row = new LinkedHashMap<>();
            for (int c = 1; c <= rs.getMetaData().getColumnCount(); c++) {
                Object value = rs.getObject(c);
                row.put(rs.getMetaData().getColumnLabel(c),
                        value instanceof BigDecimal n ? n.toPlainString() : value == null ? "" : value.toString());
            }
            return row;
        });
    }

    /** formas_pago_compras con el pago nuevo (Caja y Suministros se arman de ahi). */
    public void replacePayment(AnnulmentModule module, Long id, PurchasePaymentRequest payment) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("module", module.name()).addValue("id", id)
                .addValue("cash", payment.cashAmount()).addValue("check", payment.checkAmount())
                .addValue("transfer", payment.transferAmount()).addValue("card", payment.cardAmount())
                .addValue("checkNumber", payment.checkAmount().signum() > 0 ? payment.checkNumber().trim() : null);
        int updated = jdbc.update("UPDATE formas_pago_compras SET efectivo = :cash, cheque = :check, transferencia = :transfer,"
                + " datafono = :card, numero_cheque = :checkNumber WHERE origen = :module AND origen_id = :id", params);
        if (updated == 0) {
            jdbc.update("INSERT INTO formas_pago_compras (origen, origen_id, efectivo, cheque, transferencia, datafono,"
                    + " numero_cheque) VALUES (:module, :id, :cash, :check, :transfer, :card, :checkNumber)", params);
        }
    }

    /**
     * Cuenta por cobrar del datafono (Q33) con el valor nuevo: se ajusta, se crea si antes no habia datafono o se
     * borra si ya no hay. Solo llega aca si estaba PENDIENTE (la correccion rechaza las demas).
     */
    public void replaceReceivable(AnnulmentModule module, Long id, CorrectedPurchase purchase, String trustedId,
            BigDecimal card) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("module", module.name()).addValue("id", id)
                .addValue("card", card).addValue("agencyId", purchase.agencyId())
                .addValue("invoice", purchase.invoiceNumber()).addValue("seller", purchase.idNumber())
                .addValue("trusted", trustedId);
        if (card.signum() == 0) {
            jdbc.update("DELETE FROM cuentas_por_cobrar WHERE modulo_compra = :module AND compra_id = :id", params);
        } else if (jdbc.update("UPDATE cuentas_por_cobrar SET valor = :card WHERE modulo_compra = :module"
                + " AND compra_id = :id", params) == 0) {
            jdbc.update("INSERT INTO cuentas_por_cobrar (agencia_id, modulo_compra, compra_id, numero_factura,"
                    + " cedula_vendedor, cedula_fiel, valor) VALUES (:agencyId, :module, :id, :invoice, :seller, :trusted,"
                    + " :card)", params);
        }
    }

    /** La entrada de inventario es una copia de la compra: se borra y se vuelve a escribir con los kilos nuevos. */
    public void deleteInventory(AnnulmentModule module, Long id) {
        jdbc.update("DELETE FROM inventarios WHERE modulo_compra = :module AND compra_id = :id",
                new MapSqlParameterSource().addValue("module", module.inventoryModule()).addValue("id", id));
    }

    /**
     * Compra de seco facturada a una compra anunciada: su saldo cambia en la diferencia de kilos netos, con la fila
     * bloqueada por el UPDATE. Falso si la compra tiene anunciada y el saldo quedaria negativo.
     */
    public boolean adjustFuturePurchase(Long dryPurchaseId, BigDecimal oldNetKg, BigDecimal newNetKg) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("id", dryPurchaseId)
                .addValue("difference", oldNetKg.subtract(newNetKg));
        int updated = jdbc.update("UPDATE compras_anunciadas a SET saldo = a.saldo + :difference FROM compras_cafe_seco p"
                + " WHERE p.id = :id AND a.id = p.compra_anunciada_id AND a.saldo + :difference >= 0", params);
        return updated == 1 || !Boolean.TRUE.equals(jdbc.queryForObject("SELECT compra_anunciada_id IS NOT NULL"
                + " FROM compras_cafe_seco WHERE id = :id", params, Boolean.class));
    }

    public void markCorrected(AnnulmentModule module, Long id, Long userId) {
        jdbc.update(MARK.get(module), new MapSqlParameterSource().addValue("id", id).addValue("userId", userId));
    }
}
