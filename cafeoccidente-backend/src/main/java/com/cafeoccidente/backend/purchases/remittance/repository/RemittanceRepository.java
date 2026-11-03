package com.cafeoccidente.backend.purchases.remittance.repository;

import com.cafeoccidente.backend.purchases.annulment.repository.AnnulmentModule;
import com.cafeoccidente.backend.purchases.remittance.dto.RemittanceLine;
import com.cafeoccidente.backend.purchases.remittance.dto.RemittancePurchase;
import com.cafeoccidente.backend.purchases.remittance.dto.RemittanceResponse;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Remesa sobre las compras de seco, verde y pasilla (en Access las tres estaban en COMPRAS y la remesa
 * marcaba COMPRAS.Plano y COMPRAS.Remision; aca, remesa_id). Tambien registra y reversa la salida de
 * empaques de la remesa (RN-11.9).
 */
@Repository
public class RemittanceRepository {

    /** Columnas de las 3 tablas con lo que piden los archivos; en pasilla, % Almendra y Peso Almendra
     *  hacen de PorcAlmSana y W_AlmSana (Form_PASILLA) y lo que no tiene va en 0. */
    private static final String COMMON = "p.id, p.agencia_id, p.fondo_id, p.especial, p.numero_factura, p.fecha_compra,"
            + " p.creado_en, p.cedula, p.nombres, p.apellidos, p.kilos_netos, p.vr_kilo, p.vr_bruto, p.pr_base_cps,"
            + " p.numero_anuncio, p.fecha_anuncio, p.estado, p.remesa_id";
    private static final String DRY_LIKE = ", p.factor, p.peso_alm_sana, p.pr_alm_sana, p.bonificacion,"
            + " p.porc_kg_pasilla, p.porc_merma";
    private static final String PURCHASES =
            "SELECT 'DRY' AS module, " + COMMON + DRY_LIKE + " FROM compras_cafe_seco p"
            + " UNION ALL SELECT 'GREEN', " + COMMON + DRY_LIKE + " FROM compras_cafe_verde p"
            + " UNION ALL SELECT 'HUSK', " + COMMON + ", p.porc_almendra, p.peso_almendra, 0, 0, 0, 0 FROM compras_pasilla p";

    /** Nombre_Vend: [1er nombre] [2o nombre] [1er apellido] [2o apellido] de TERCEROS; si el tercero no
     *  existe, el nombre guardado en la compra (el INNER JOIN de Access la dejaba por fuera). */
    private static final String SELLER = "COALESCE(NULLIF(regexp_replace(trim(concat_ws(' ', t.primer_nombre,"
            + " t.segundo_nombre, t.primer_apellido, t.segundo_apellido)), '\\s+', ' ', 'g'), ''),"
            + " trim(concat_ws(' ', c.nombres, c.apellidos))) AS seller_name";
    private static final String JOINS = " JOIN fondos f ON f.id = c.fondo_id"
            + " LEFT JOIN registros_control rc ON rc.agencia_id = c.agencia_id AND rc.activo"
            + " LEFT JOIN LATERAL (SELECT * FROM terceros t WHERE t.identificacion = c.cedula"
            + " ORDER BY (t.agencia_id = c.agencia_id) DESC, t.id LIMIT 1) t ON true";
    private static final String PENDING = " c.remesa_id IS NULL AND c.estado = 'VALIDA' AND c.kilos_netos > 0"
            + " AND c.agencia_id = :agencyId AND f.codigo = :fundCode";

    private static final String RESPONSE = "SELECT r.id, r.numero_remesa, f.codigo, r.especial_filtro,"
            + " r.cantidad_empaques, r.tipo_empaque, r.estado, r.creado_en,"
            + " COALESCE(x.n, 0) AS purchases, COALESCE(x.kg, 0) AS kg FROM remesas r JOIN fondos f ON f.id = r.fondo_id"
            + " LEFT JOIN (SELECT remesa_id, count(*) AS n, sum(kilos_netos) AS kg FROM (SELECT remesa_id, kilos_netos"
            + " FROM compras_cafe_seco UNION ALL SELECT remesa_id, kilos_netos FROM compras_cafe_verde"
            + " UNION ALL SELECT remesa_id, kilos_netos FROM compras_pasilla) u GROUP BY remesa_id) x ON x.remesa_id = r.id";

    /** SQL fijo por modulo: el nombre de la tabla nunca sale del request (solo el enum elige el texto). */
    private static final Map<AnnulmentModule, String> LOCK = Map.of(
            AnnulmentModule.DRY, lockSql("compras_cafe_seco"),
            AnnulmentModule.GREEN, lockSql("compras_cafe_verde"),
            AnnulmentModule.HUSK, lockSql("compras_pasilla"));
    private static final Map<AnnulmentModule, String> ASSIGN = Map.of(
            AnnulmentModule.DRY, "UPDATE compras_cafe_seco SET remesa_id = :remittanceId WHERE id IN (:ids)",
            AnnulmentModule.GREEN, "UPDATE compras_cafe_verde SET remesa_id = :remittanceId WHERE id IN (:ids)",
            AnnulmentModule.HUSK, "UPDATE compras_pasilla SET remesa_id = :remittanceId WHERE id IN (:ids)");
    private static final List<String> RELEASE = List.of(
            "UPDATE compras_cafe_seco SET remesa_id = NULL WHERE remesa_id = :id",
            "UPDATE compras_cafe_verde SET remesa_id = NULL WHERE remesa_id = :id",
            "UPDATE compras_pasilla SET remesa_id = NULL WHERE remesa_id = :id");

    private static String lockSql(String table) {
        return "SELECT p.id, p.agencia_id, f.codigo, p.especial, p.estado, p.kilos_netos, p.remesa_id, p.numero_factura"
                + " FROM " + table + " p JOIN fondos f ON f.id = p.fondo_id WHERE p.id IN (:ids) FOR UPDATE OF p";
    }

    /** Estado de una compra al armar la remesa, leida con bloqueo de fila. */
    public record LockedPurchase(Long id, Long agencyId, String fundCode, String specialType, String status,
            BigDecimal netKg, Long remittanceId, Integer invoiceNumber) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    public RemittanceRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** RN-11.1: los Especiales que tienen facturas pendientes en el fondo. */
    public List<String> specials(Long agencyId, String fundCode) {
        return jdbc.queryForList("SELECT DISTINCT c.especial FROM (" + PURCHASES + ") c" + JOINS + " WHERE" + PENDING
                + " ORDER BY c.especial", params(agencyId, fundCode), String.class);
    }

    /** RN-11.2: facturas pendientes del fondo y del Especial elegido (null = "Todos"), por factura. */
    public List<RemittancePurchase> pending(Long agencyId, String fundCode, String specialType) {
        String sql = "SELECT c.module, c.id, rc.prefijo, c.numero_factura, c.fecha_compra, c.especial, c.cedula, "
                + SELLER + ", c.kilos_netos, c.vr_bruto FROM (" + PURCHASES + ") c" + JOINS + " WHERE" + PENDING
                + " AND (CAST(:specialType AS VARCHAR) IS NULL OR c.especial = :specialType)"
                + " ORDER BY c.numero_factura, c.module";
        return jdbc.query(sql, params(agencyId, fundCode).addValue("specialType", specialType), (rs, i) ->
                new RemittancePurchase(AnnulmentModule.valueOf(rs.getString("module")), rs.getLong("id"),
                        invoice(rs), rs.getObject("fecha_compra", LocalDate.class), rs.getString("especial"),
                        rs.getString("cedula"), rs.getString("seller_name"), rs.getBigDecimal("kilos_netos"),
                        rs.getBigDecimal("vr_bruto")));
    }

    /** Las compras pedidas, bloqueadas hasta el fin de la transaccion: dos remesas a la vez no toman la misma. */
    public List<LockedPurchase> lock(AnnulmentModule module, List<Long> ids) {
        return jdbc.query(LOCK.get(module), new MapSqlParameterSource("ids", ids), (rs, i) -> new LockedPurchase(
                rs.getLong("id"), rs.getLong("agencia_id"), rs.getString("codigo"), rs.getString("especial"),
                rs.getString("estado"), rs.getBigDecimal("kilos_netos"), (Long) rs.getObject("remesa_id"),
                (Integer) rs.getObject("numero_factura")));
    }

    public Long insert(Long agencyId, Long fundId, int sequence, String number, String specialType,
            int packagingQuantity, String packagingType, Long userId) {
        return jdbc.queryForObject("INSERT INTO remesas (agencia_id, fondo_id, consecutivo, numero_remesa,"
                + " especial_filtro, cantidad_empaques, tipo_empaque, creado_por_usuario_id, creado_en)"
                + " VALUES (:agencyId, :fundId, :sequence, :number, :specialType, :quantity, :type, :userId, now())"
                + " RETURNING id", new MapSqlParameterSource().addValue("agencyId", agencyId).addValue("fundId", fundId)
                        .addValue("sequence", sequence).addValue("number", number).addValue("specialType", specialType)
                        .addValue("quantity", packagingQuantity).addValue("type", packagingType)
                        .addValue("userId", userId), Long.class);
    }

    /** RN-11.6: las facturas quedan con la remesa y salen de pendientes. */
    public void assign(AnnulmentModule module, List<Long> ids, Long remittanceId) {
        jdbc.update(ASSIGN.get(module), new MapSqlParameterSource().addValue("remittanceId", remittanceId).addValue("ids", ids));
    }

    /** RN-11.9: salida en EMPAQUES con el numero de la remesa como transaccion y el detalle "Remision". */
    public void addPackagingExit(Long agencyId, String number, String packagingType, String idNumber, int quantity) {
        jdbc.update("INSERT INTO empaques (id_transaccion, agencia_id, tipo_empaque, fecha, cedula, detalle, entradas,"
                + " salidas, creado_en) VALUES (:number, :agencyId, :type, CURRENT_DATE, :idNumber, 'Remision', 0,"
                + " :quantity, now())", new MapSqlParameterSource().addValue("number", number)
                        .addValue("agencyId", agencyId).addValue("type", packagingType).addValue("idNumber", idNumber)
                        .addValue("quantity", quantity));
    }

    public Optional<RemittanceResponse> find(Long agencyId, Long id) {
        return jdbc.query(RESPONSE + " WHERE r.agencia_id = :agencyId AND r.id = :id",
                new MapSqlParameterSource().addValue("agencyId", agencyId).addValue("id", id), this::toResponse)
                .stream().findFirst();
    }

    /** La remesa de la agencia, bloqueada para desmarcarla. */
    public Optional<RemittanceResponse> lockRemittance(Long agencyId, Long id) {
        jdbc.queryForList("SELECT id FROM remesas WHERE agencia_id = :agencyId AND id = :id FOR UPDATE",
                new MapSqlParameterSource().addValue("agencyId", agencyId).addValue("id", id));
        return find(agencyId, id);
    }

    public List<RemittanceResponse> findByNumber(Long agencyId, String number) {
        return jdbc.query(RESPONSE + " WHERE r.agencia_id = :agencyId AND upper(r.numero_remesa) = upper(:number)",
                new MapSqlParameterSource().addValue("agencyId", agencyId).addValue("number", number.trim()),
                this::toResponse);
    }

    /** desmarcaremesa: las facturas vuelven a pendientes, la salida de empaques se borra y la remesa queda
     *  DESMARCADA. El numero no se reutiliza. */
    public void unmark(Long agencyId, Long id, String number, Long userId) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("id", id).addValue("agencyId", agencyId)
                .addValue("number", number).addValue("userId", userId);
        RELEASE.forEach(sql -> jdbc.update(sql, params));
        jdbc.update("DELETE FROM empaques WHERE agencia_id = :agencyId AND id_transaccion = :number"
                + " AND detalle = 'Remision'", params);
        jdbc.update("UPDATE remesas SET estado = 'DESMARCADA', desmarcado_por_usuario_id = :userId,"
                + " desmarcado_en = now() WHERE id = :id", params);
    }

    /** Fecha y hora de la remesa y agencia (en mayusculas, como COMPRAS.Agencia en Access). */
    public record Header(String agencyName, LocalDateTime generatedAt) {
    }

    public Header header(Long id) {
        return jdbc.queryForObject("SELECT upper(a.nombre) AS agencia, r.creado_en FROM remesas r"
                + " JOIN agencias a ON a.id = r.agencia_id WHERE r.id = :id", new MapSqlParameterSource("id", id),
                (rs, i) -> new Header(rs.getString("agencia"), localDateTime(rs.getTimestamp("creado_en"))));
    }

    /** Una fila por factura de la remesa, por numero de factura. */
    public List<RemittanceLine> lines(Long id) {
        String sql = "SELECT c.*, rc.prefijo, rc.tamano_muestra, f.codigo AS fund_code, an.pr_base_cps AS anuncio_pr_base_cps, "
                + SELLER + " FROM (" + PURCHASES + ") c" + JOINS
                + " LEFT JOIN anuncios_agencias aa ON aa.agencia_id = c.agencia_id"
                + " AND aa.numero_anuncio::text = substring(c.numero_anuncio from '([0-9]+)$')"
                + " LEFT JOIN anuncios an ON an.id = aa.anuncio_id"
                + " WHERE c.remesa_id = :id ORDER BY c.numero_factura, c.module";
        return jdbc.query(sql, new MapSqlParameterSource("id", id), (rs, i) -> toLine(rs));
    }

    private static RemittanceLine toLine(ResultSet rs) throws SQLException {
        BigDecimal kg = number(rs, "kilos_netos");
        BigDecimal factor = number(rs, "factor");
        BigDecimal bonus = number(rs, "bonificacion");
        return new RemittanceLine(rs.getString("cedula"), rs.getString("seller_name"),
                localDateTime(rs.getTimestamp("creado_en")), invoice(rs), rs.getString("fund_code"),
                rs.getString("especial"), rs.getObject("fecha_anuncio", LocalDate.class), kg, factor,
                kg.multiply(factor), number(rs, "vr_bruto"), number(rs, "vr_kilo"), number(rs, "pr_alm_sana"), bonus,
                RemittanceLine.healthyPercentage(number(rs, "peso_alm_sana"), rs.getBigDecimal("tamano_muestra")),
                number(rs, "porc_kg_pasilla"), number(rs, "porc_merma"), number(rs, "anuncio_pr_base_cps"),
                bonus.multiply(kg), number(rs, "pr_base_cps"));
    }

    private RemittanceResponse toResponse(ResultSet rs, int i) throws SQLException {
        return new RemittanceResponse(rs.getLong("id"), rs.getString("numero_remesa"), rs.getString("codigo"),
                rs.getString("especial_filtro"), (Integer) rs.getObject("cantidad_empaques"),
                rs.getString("tipo_empaque"), rs.getString("estado"), rs.getTimestamp("creado_en").toInstant(),
                rs.getInt("purchases"), rs.getBigDecimal("kg"));
    }

    /** Factura = [prefijo] & [factura], como Access. */
    private static String invoice(ResultSet rs) throws SQLException {
        String prefix = rs.getString("prefijo");
        return (prefix == null ? "" : prefix) + rs.getInt("numero_factura");
    }

    private static BigDecimal number(ResultSet rs, String column) throws SQLException {
        BigDecimal value = rs.getBigDecimal(column);
        return value == null ? BigDecimal.ZERO : value;
    }

    private static LocalDateTime localDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private static MapSqlParameterSource params(Long agencyId, String fundCode) {
        return new MapSqlParameterSource().addValue("agencyId", agencyId).addValue("fundCode", fundCode);
    }
}
