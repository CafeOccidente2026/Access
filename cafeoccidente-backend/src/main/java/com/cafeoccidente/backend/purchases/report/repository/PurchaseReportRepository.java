package com.cafeoccidente.backend.purchases.report.repository;

import com.cafeoccidente.backend.purchases.report.dto.PurchaseReportRow;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Lectura de las compras de seco, verde y pasilla para los reportes de Compras (RF-24, RF-25). */
@Repository
public class PurchaseReportRepository {

    /** Columnas comunes de las 3 tablas de compra. */
    private static final String COLUMNS = "p.agencia_id, p.fondo_id, p.producto_id, p.numero_anuncio, p.especial,"
            + " p.fecha_compra, p.numero_factura, p.cedula, p.nombres, p.apellidos, p.kilos_netos, p.vr_kilo,"
            + " p.vr_bruto, p.vr_inventario, p.aporte_asociado, p.descuento_cooperativo, p.descuento_financiero,"
            + " p.retefuente, p.otros_descuentos, p.neto_a_pagar, p.forma_pago, p.estado";
    /** Por modulo: Kilos Verdes (kv) y la columna Porc; NULL = no aplica. */
    private static final String PURCHASES =
            "SELECT 'DRY' AS module, " + COLUMNS + ", NULL::numeric AS kv, p.factor AS porc FROM compras_cafe_seco p"
            + " UNION ALL SELECT 'GREEN', " + COLUMNS + ", p.kilos_verdes, p.factor FROM compras_cafe_verde p"
            + " UNION ALL SELECT 'HUSK', " + COLUMNS + ", NULL::numeric, p.porc_almendra FROM compras_pasilla p";

    private final NamedParameterJdbcTemplate jdbc;

    public PurchaseReportRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Las compras de la agencia entre dos fechas (ambas incluidas), por numero de factura. */
    public List<PurchaseReportRow> byDate(Long agencyId, LocalDate from, LocalDate to) {
        String sql = "SELECT c.module, a.nombre AS agency_name, c.numero_anuncio, c.especial, pr.codigo, pr.nombre AS product_name,"
                + " c.fecha_compra, f.codigo AS fund_code, rc.prefijo, c.numero_factura, c.cedula, c.nombres, c.apellidos,"
                + " c.kv, c.kilos_netos, c.porc, c.vr_kilo, c.vr_bruto, c.vr_inventario, c.aporte_asociado,"
                + " c.descuento_cooperativo, c.descuento_financiero, c.retefuente, c.otros_descuentos, c.neto_a_pagar,"
                + " CASE WHEN c.estado = 'ANULADA' THEN 'ANULADA' ELSE c.forma_pago END AS forma_pago, c.estado"
                + " FROM (" + PURCHASES + ") c"
                + " JOIN agencias a ON a.id = c.agencia_id JOIN fondos f ON f.id = c.fondo_id"
                + " JOIN productos pr ON pr.id = c.producto_id"
                + " LEFT JOIN registros_control rc ON rc.agencia_id = c.agencia_id AND rc.activo"
                + " WHERE c.agencia_id = :agencyId AND c.fecha_compra BETWEEN :from AND :to"
                + " ORDER BY c.numero_factura, c.module";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("agencyId", agencyId).addValue("from", from).addValue("to", to);
        return jdbc.query(sql, params, (rs, i) -> new PurchaseReportRow(
                rs.getString("module"), rs.getString("agency_name"), rs.getString("numero_anuncio"),
                rs.getString("especial"), rs.getString("codigo"), rs.getString("product_name"),
                rs.getObject("fecha_compra", LocalDate.class), rs.getString("fund_code"), rs.getString("prefijo"),
                (Integer) rs.getObject("numero_factura"), rs.getString("cedula"), rs.getString("nombres"),
                rs.getString("apellidos"), rs.getBigDecimal("kv"), rs.getBigDecimal("kilos_netos"),
                rs.getBigDecimal("porc"), rs.getBigDecimal("vr_kilo"), rs.getBigDecimal("vr_bruto"),
                rs.getBigDecimal("vr_inventario"), rs.getBigDecimal("aporte_asociado"),
                rs.getBigDecimal("descuento_cooperativo"), rs.getBigDecimal("descuento_financiero"),
                rs.getBigDecimal("retefuente"), rs.getBigDecimal("otros_descuentos"), rs.getBigDecimal("neto_a_pagar"),
                rs.getString("forma_pago"), rs.getString("estado")));
    }
}
