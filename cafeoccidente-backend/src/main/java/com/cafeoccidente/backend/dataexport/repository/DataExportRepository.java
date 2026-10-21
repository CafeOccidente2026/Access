package com.cafeoccidente.backend.dataexport.repository;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Consultas de origen de "ExportarCompras" (ComprasParaExportar, SociosparaExportar,
 * FutureBuystoExport, SalidasparaExportar, AnunciosParaExportar) armadas sobre las tablas del sistema
 * nuevo, con las columnas de Access en su orden. NULL = el sistema nuevo no guarda ese dato (celda
 * vacia en el Excel, nunca un cero inventado). agencyId null = todas las agencias.
 *
 * <p>Limitacion conocida: el historico de INVENTARIO y [COMPRAS A FUTURO] de El Tambo no se migro a
 * inventory_movement/remission/future_purchase (quedo en staging_legacy_*), asi que Inventario y
 * FutureBuys solo traen lo registrado en la web.
 */
@Repository
public class DataExportRepository {

    public static final List<String> PURCHASE_COLUMNS = List.of(
            "Anuncio", "Agencia", "Fecha_Compra", "Fecha_Anuncio", "Cod_Prod", "Fondo", "Factura", "Prefijo", "Cedula",
            "Sacos", "Kilos_Brutos", "Destare", "Kilos_Verdes", "Kilos_Netos", "Pr_AlmSana", "Pr_AlmDefec",
            "Vr_Kilo_Comp", "W_TotAlm", "W_AlmSana", "W_AlmDefec", "Bonificación", "PorcAlmSana", "PorcAlmDefec",
            "PorcMerma", "VrIncCalidad", "IncCalidad", "Pr_Base_CPS", "Pr_Base_PC", "Castigo", "Vr_Inventario",
            "Vr_Kilo", "Vr_Bruto", "Aporte_Socio", "Descuento_Coop", "Descuento_Fro", "Retefuente", "Neto_a_Pagar",
            "OtrosDescuentos", "Forma_de_Pago", "NumCheque", "Especial", "En_Inventario", "Exportado", "En_Caja",
            "En_Sum", "Plano", "Nuevo", "Remision", "EnProg", "EnCVRN", "Costos", "CCosto", "SCCosto", "CodVend",
            "CodCiudad", "CodBodega", "TipoComp", "CodComp", "Fiel", "Espp", "FPef", "FPch", "FPtx", "FPdat");

    public static final List<String> GROWER_COLUMNS = List.of(
            "IdAsociado", "1er Nombre", "2o Nombre", "1er Apellido", "2o Apellido", "Agencia", "FechaNacimiento",
            "LugarNacimiento", "EstadoCivil", "CedulaCafetera", "Direccion", "Telefono", "FechaAfiliación", "Acta",
            "Aceptado", "Habil", "Retirado", "Fallecido", "Observacion", "Tipo", "Emp_Transp", "Vehiculo", "Exportado",
            "Asociacion", "Ciu", "Sexo", "Pais", "CodigoPostal", "Email");

    public static final List<String> FUTURE_COLUMNS = List.of(
            "Anuncio", "Agencia", "Fecha_Anuncio", "Cedula", "Kilos_Anunciados", "Kilos_Entregados", "Saldo_Kilos",
            "Pr_AlmSana", "Pr_AlmDefec", "Costos", "Bonificación", "VrIncCalidad", "prbcps", "Especial",
            "Fecha_Entrega", "Exportado", "Finca", "Mpio", "Vereda", "Id_NessOcci");

    public static final List<String> INVENTORY_COLUMNS = List.of(
            "Agencia", "Cod_Prod", "Factura", "Fecha", "Kilos_Netos", "PorcAlmSana", "Vr_Inventario", "Remisión",
            "Cantidad", "PorcAlmSanaSl", "Valor unitario", "Vr_Salida", "Destino", "Especial", "Exportado", "Sacos",
            "Kilos_Brutos", "Conductor");

    public static final List<String> ANNOUNCEMENT_COLUMNS = List.of(
            "Anuncio", "Agencia", "Fecha", "Pr_Base_CPS", "Pr_AlmSana", "Pr_AlmDefec", "Bonificacion", "Costos",
            "Especial", "Cupo", "Entregados", "Saldo", "Exportado");

    /** Tablas de compras -> clave de purchase_payment_split (misma que SuppliesReadRepository). */
    public static final Map<String, String> PURCHASE_TABLES = Map.of(
            "compras_cafe_seco", "DRY",
            "compras_cafe_verde", "GREEN",
            "compras_pasilla", "HUSK",
            "compras_cafes_otros", "OTHER",
            "compras_ferti_futuro", "FERTI");

    /** Tablas que se marcan como exportadas, con la fecha que usa "Exportado Especial". Es la lista
     *  fija de tablas permitidas: el nombre de tabla va concatenado al SQL y no puede salir de afuera. */
    private static final Map<String, String> EXPORT_DATE_COLUMNS = exportDateColumns();

    /** Numero de anuncio de Access: las compras nuevas lo guardan como "SDTA-3510". */
    private static final String PLAIN_ANNOUNCEMENT = "regexp_replace(p.numero_anuncio, '^.*-', '')";

    /** Columnas comunes a las 5 tablas de compras; Forma_de_Pago en COMPRAS es el estado de la factura
     *  (VALIDA / ANULADA, columna status) y el pago real va en FPef/FPch/FPtx/FPdat. */
    private static final Map<String, String> PURCHASE_COMMON = Map.ofEntries(
            Map.entry("Anuncio", PLAIN_ANNOUNCEMENT),
            Map.entry("Agencia", "a.nombre_access"),
            Map.entry("Fecha_Compra", "p.fecha_compra"),
            Map.entry("Fecha_Anuncio", "p.fecha_anuncio"),
            Map.entry("Cod_Prod", "pc.codigo"),
            Map.entry("Fondo", "f.codigo"),
            Map.entry("Factura", "p.numero_factura"),
            Map.entry("Prefijo", "a.prefijo_factura"),
            Map.entry("Cedula", "p.cedula"),
            Map.entry("Kilos_Brutos", "p.kilos_brutos"),
            // Solo Cafe Verde llena Kilos_Verdes; en Access queda en 0 en el resto (844/844 compras de
            // El Tambo, y SumaDeKilos_Verdes = 0 en comprasTotal.XLS).
            Map.entry("Kilos_Verdes", "0"),
            Map.entry("Destare", "p.destare"),
            Map.entry("Kilos_Netos", "p.kilos_netos"),
            Map.entry("Pr_Base_CPS", "m.pr_base_cps"),
            Map.entry("Vr_Inventario", "p.vr_inventario"),
            Map.entry("Vr_Kilo", "p.vr_kilo"),
            Map.entry("Vr_Bruto", "p.vr_bruto"),
            Map.entry("Aporte_Socio", "p.aporte_asociado"),
            Map.entry("Descuento_Coop", "p.descuento_cooperativo"),
            Map.entry("Retefuente", "p.retefuente"),
            Map.entry("Neto_a_Pagar", "p.neto_a_pagar"),
            Map.entry("OtrosDescuentos", "p.otros_descuentos"),
            Map.entry("Forma_de_Pago", "p.estado"),
            Map.entry("NumCheque", "CASE WHEN p.numero_cheque ~ '^[0-9]{1,9}$' THEN CAST(p.numero_cheque AS INTEGER) END"),
            Map.entry("Especial", "p.especial"),
            Map.entry("Exportado", "p.exportado"),
            Map.entry("Costos", "p.costos"),
            // COMPRAS.Fiel = RegControl.Fiel de la agencia (87304051 en las 844 compras de El Tambo).
            Map.entry("Fiel", "CASE WHEN cr.cedula_fiel ~ '^[0-9]{1,15}$' THEN CAST(cr.cedula_fiel AS NUMERIC) END"),
            Map.entry("FPef", payment("s.efectivo", "EFECTIVO")),
            Map.entry("FPch", payment("s.cheque", "CHEQUE")),
            Map.entry("FPtx", payment("s.transferencia", "TRANSFERENCIA")),
            Map.entry("FPdat", payment("s.datafono", "DATAFONO")));

    /** Lo propio de cada formulario de compra (ver los Javadoc de cada entidad). */
    private static final Map<String, Map<String, String>> PURCHASE_SPECIFIC = Map.of(
            "compras_cafe_seco", dryLike(),
            "compras_cafes_otros", dryLike(),
            "compras_cafe_verde", Map.of(
                    "Sacos", "p.sacos", "Kilos_Verdes", "p.kilos_verdes", "Pr_AlmSana", "p.pr_alm_sana",
                    "Pr_AlmDefec", "p.pr_alm_defec", "Vr_Kilo_Comp", "p.vr_kilo_comp", "Bonificación", "p.bonificacion",
                    "Pr_Base_PC", "p.pr_base_cps", "Castigo", "p.castigo", "Descuento_Fro", "p.descuento_financiero"),
            "compras_pasilla", Map.of(
                    "Sacos", "p.sacos", "Pr_AlmSana", "p.pr_punto", "W_AlmSana", "p.peso_almendra",
                    "PorcAlmSana", "p.porc_almendra", "Pr_Base_CPS", "p.pr_base_cps",
                    "Descuento_Fro", "p.descuento_financiero"),
            "compras_ferti_futuro", Map.ofEntries(
                    Map.entry("Sacos", "p.sacos"), Map.entry("Pr_AlmSana", "p.pr_alm_sana"),
                    Map.entry("Pr_AlmDefec", "p.pr_alm_defec"), Map.entry("W_AlmSana", "p.peso_alm_sana"),
                    Map.entry("W_AlmDefec", "p.peso_tot_pasilla"), Map.entry("Bonificación", "p.bonificacion"),
                    Map.entry("PorcAlmSana", "p.factor"), Map.entry("PorcAlmDefec", "p.porc_kg_pasilla"),
                    Map.entry("VrIncCalidad", "p.pr_inc_calidad"),
                    Map.entry("IncCalidad", "p.vr_inc_calidad"), Map.entry("Castigo", "p.castigo"),
                    Map.entry("Descuento_Fro", "p.descuento_financiero")));

    private static final String AGENCY_FILTER = "(CAST(:agencyId AS BIGINT) IS NULL OR %s = :agencyId)";

    private final NamedParameterJdbcTemplate jdbc;

    public DataExportRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static Map<String, String> exportDateColumns() {
        Map<String, String> columns = new LinkedHashMap<>();
        PURCHASE_TABLES.keySet().forEach(t -> columns.put(t, "fecha_compra"));
        columns.put("terceros", "fecha_afiliacion");
        columns.put("compras_anunciadas", "fecha_anuncio");
        columns.put("inventarios", "fecha_compra");
        columns.put("remisiones", "fecha_remision");
        columns.put("anuncios_agencias", "fecha_asignacion");
        return columns;
    }

    private static String requireAllowedTable(String table, Collection<String> allowed) {
        if (!allowed.contains(table)) {
            throw new IllegalArgumentException("Tabla no permitida en la exportacion: " + table);
        }
        return table;
    }

    private static Map<String, String> dryLike() {
        return Map.ofEntries(
                Map.entry("Sacos", "p.sacos"), Map.entry("Pr_AlmSana", "p.pr_alm_sana"),
                Map.entry("Pr_AlmDefec", "p.pr_alm_defec"), Map.entry("W_TotAlm", "p.peso_tot_alm"),
                Map.entry("W_AlmSana", "p.peso_alm_sana"), Map.entry("W_AlmDefec", "p.peso_tot_pasilla"),
                Map.entry("Bonificación", "p.bonificacion"), Map.entry("PorcAlmSana", "p.factor"),
                Map.entry("PorcAlmDefec", "p.porc_kg_pasilla"), Map.entry("PorcMerma", "p.porc_merma"),
                Map.entry("Pr_Base_PC", "p.pr_base_cps"), Map.entry("Castigo", "p.castigo"),
                Map.entry("Descuento_Fro", "p.descuento_financiero"));
    }

    /** Pago mixto migrado: el monto de purchase_payment_split; si no, el neto entero va a su forma de pago. */
    private static String payment(String splitColumn, String method) {
        return "CASE WHEN s.origen_id IS NOT NULL THEN " + splitColumn
                + " WHEN p.forma_pago = '" + method + "' THEN p.neto_a_pagar ELSE 0 END";
    }

    private static String purchaseSelect(String table) {
        Map<String, String> specific = PURCHASE_SPECIFIC.get(requireAllowedTable(table, PURCHASE_TABLES.keySet()));
        String columns = PURCHASE_COLUMNS.stream()
                // NULL tipado: en el UNION una columna puede ser numerica en un modulo y vacia en otro.
                .map(c -> specific.getOrDefault(c, PURCHASE_COMMON.getOrDefault(c, "CAST(NULL AS NUMERIC)")) + " AS \"" + c + "\"")
                .collect(Collectors.joining(", "));
        return "SELECT '" + table + "' AS source_table, p.id AS source_id, " + columns
                + " FROM " + table + " p JOIN agencias a ON a.id = p.agencia_id JOIN fondos f ON f.id = p.fondo_id"
                + " JOIN productos pc ON pc.id = p.producto_id"
                + " LEFT JOIN anuncios_agencias aan ON aan.agencia_id = p.agencia_id"
                + " AND CAST(aan.numero_anuncio AS TEXT) = " + PLAIN_ANNOUNCEMENT
                + " LEFT JOIN anuncios m ON m.id = aan.anuncio_id"
                + " LEFT JOIN registros_control cr ON cr.agencia_id = p.agencia_id AND cr.activo"
                + " LEFT JOIN formas_pago_compras s ON s.origen = '" + PURCHASE_TABLES.get(table) + "' AND s.origen_id = p.id"
                + " WHERE " + AGENCY_FILTER.formatted("p.agencia_id") + " AND (:all OR NOT p.exportado)";
    }

    /** ComprasParaExportar (pendingOnly) o todas las compras (base de ComprasTotal, que no filtra). */
    public List<ExportRow> purchases(Long agencyId, boolean pendingOnly) {
        String sql = PURCHASE_TABLES.keySet().stream().sorted().map(DataExportRepository::purchaseSelect)
                .collect(Collectors.joining(" UNION ALL "))
                + " ORDER BY \"Fecha_Compra\", \"Factura\"";
        return query(sql, agencyId, pendingOnly);
    }

    /** SociosparaExportar: Asociados con Exportado = No (nulo cuenta como No, igual que un Si/No de Access). */
    public List<ExportRow> growers(Long agencyId, boolean pendingOnly) {
        String sql = "SELECT 'terceros' AS source_table, g.id AS source_id, g.identificacion, g.primer_nombre, g.segundo_nombre,"
                + " g.primer_apellido, g.segundo_apellido, a.nombre_access, g.fecha_nacimiento, g.lugar_nacimiento, g.estado_civil,"
                + " g.cedula_cafetera, g.direccion, g.telefono, g.fecha_afiliacion, g.acta, g.aceptado, g.habil,"
                + " g.retirado, g.fallecido, g.observacion, g.tipo, g.empresa_transporte, g.vehiculo,"
                + " COALESCE(g.exportado, FALSE), g.asociacion, g.ciudad, g.sexo, g.pais, g.codigo_postal, g.email"
                + " FROM terceros g LEFT JOIN agencias a ON a.id = g.agencia_id"
                + " WHERE " + AGENCY_FILTER.formatted("g.agencia_id") + " AND (:all OR NOT COALESCE(g.exportado, FALSE))"
                + " ORDER BY g.id";
        return query(sql, agencyId, pendingOnly);
    }

    /** FutureBuystoExport. Kilos_Entregados = Kilos_Anunciados - Saldo_Kilos (el sistema nuevo guarda el saldo). */
    public List<ExportRow> futurePurchases(Long agencyId, boolean pendingOnly) {
        String sql = "SELECT 'compras_anunciadas' AS source_table, p.id AS source_id, " + PLAIN_ANNOUNCEMENT + ","
                + " a.nombre_access, p.fecha_anuncio, p.cedula, p.kilos,"
                + " p.kilos - p.saldo, p.saldo, p.pr_alm_sana, p.pr_alm_defec,"
                + " p.costos, p.bonificacion, p.pr_inc_calidad, p.pr_base_cps, p.especial, p.fecha_entrega,"
                + " p.exportado, p.finca, p.municipio, p.vereda, NULL"
                + " FROM compras_anunciadas p JOIN agencias a ON a.id = p.agencia_id"
                + " WHERE " + AGENCY_FILTER.formatted("p.agencia_id") + " AND (:all OR NOT p.exportado)"
                + " ORDER BY p.id";
        return query(sql, agencyId, pendingOnly);
    }

    /**
     * SalidasparaExportar: INVENTARIO de Access tiene entradas (COPIACOMPRAS: Factura, Kilos_Netos,
     * PorcAlmSana, Vr_Inventario y Conductor = cedula del vendedor) y salidas (Remision, Cantidad,
     * PorcAlmSanaSl, Valor unitario, Vr_Salida, Destino, Sacos, Kilos_Brutos, Conductor). Aca las
     * entradas son inventory_movement y las salidas remission_line (se marcan por remision).
     */
    public List<ExportRow> inventory(Long agencyId, boolean pendingOnly) {
        String seller = "COALESCE(d.cedula, gr.cedula, h.cedula, o.cedula, fe.cedula)";
        String entries = "SELECT 'inventarios' AS source_table, im.id AS source_id, a.nombre_access,"
                + " pc.codigo, im.numero_factura, im.fecha_compra AS fecha, im.kilos_netos, im.factor,"
                + " im.vr_inventario, NULL, NULL, NULL, NULL, NULL, NULL, im.especial, im.exportado, NULL, NULL, "
                + seller
                + " FROM inventarios im JOIN agencias a ON a.id = im.agencia_id"
                + " JOIN productos pc ON pc.id = im.producto_id"
                + " LEFT JOIN compras_cafe_seco d ON im.modulo_compra = 'DRY_COFFEE' AND d.id = im.compra_id"
                + " LEFT JOIN compras_cafe_verde gr ON im.modulo_compra = 'GREEN_COFFEE' AND gr.id = im.compra_id"
                + " LEFT JOIN compras_pasilla h ON im.modulo_compra = 'HUSK' AND h.id = im.compra_id"
                + " LEFT JOIN compras_cafes_otros o ON im.modulo_compra = 'OTHER_COFFEE' AND o.id = im.compra_id"
                + " LEFT JOIN compras_ferti_futuro fe ON im.modulo_compra = 'FERTI_FUTURO' AND fe.id = im.compra_id"
                + " WHERE " + AGENCY_FILTER.formatted("im.agencia_id") + " AND (:all OR NOT im.exportado)";
        String exits = "SELECT 'remisiones' AS source_table, r.id AS source_id, a.nombre_access, pc.codigo, NULL,"
                + " r.fecha_remision AS fecha, NULL, NULL, NULL, r.numero_remision, rl.cantidad, rl.factor_salida,"
                + " rl.valor_unitario, rl.valor_salida, r.destino, im.especial, r.exportado, rl.sacos, rl.kilos_brutos,"
                + " c.identificacion"
                + " FROM remisiones_detalle rl JOIN remisiones r ON r.id = rl.remision_id"
                + " JOIN inventarios im ON im.id = rl.inventario_id"
                + " JOIN agencias a ON a.id = r.agencia_id JOIN productos pc ON pc.id = im.producto_id"
                + " LEFT JOIN terceros c ON c.id = r.conductor_tercero_id"
                + " WHERE " + AGENCY_FILTER.formatted("r.agencia_id") + " AND (:all OR NOT r.exportado)";
        return query(entries + " UNION ALL " + exits + " ORDER BY fecha, source_table, source_id", agencyId, pendingOnly);
    }

    /**
     * AnunciosParaExportar: una fila de ANUNCIOS por agencia = agency_announcement_number. Los anuncios
     * migrados de Access traen Pr_AlmSana/Bonificacion/Costos congelados en el maestro; los publicados
     * en la web se calculan como AnnouncementServiceImpl.toResponse con el ControlRecord de la agencia.
     * Cupo/Entregados/Saldo solo si hay cupo asignado (AnnouncementQuotaServiceImpl: compras de Seco).
     */
    public List<ExportRow> announcements(Long agencyId, boolean pendingOnly) {
        String sql = "SELECT 'anuncios_agencias' AS source_table, aan.id AS source_id,"
                + " aan.numero_anuncio, a.nombre_access, aan.fecha_asignacion, m.pr_base_cps,"
                + " CASE WHEN m.pr_alm_sana IS NOT NULL THEN m.pr_alm_sana"
                + "  WHEN m.pr_punto IS NOT NULL THEN m.pr_punto"
                + "  ELSE ROUND(m.pr_base_cps / cr.base_carga, 2) - cr.costos END,"
                + " m.pr_alm_defec,"
                + " CASE WHEN m.pr_alm_sana IS NOT NULL THEN m.bonificacion"
                + "  ELSE COALESCE(ROUND(m.sobrepr_carga_esp / cr.base_carga, 2), 0) END,"
                + " CASE WHEN m.pr_alm_sana IS NOT NULL THEN m.costos ELSE cr.costos END,"
                + " m.especial, q.cupo, del.kg, q.cupo - del.kg, aan.exportado"
                + " FROM anuncios_agencias aan JOIN anuncios m ON m.id = aan.anuncio_id"
                + " JOIN agencias a ON a.id = aan.agencia_id"
                + " LEFT JOIN registros_control cr ON cr.agencia_id = aan.agencia_id AND cr.activo"
                + " LEFT JOIN cupos_anuncios q ON q.anuncio_agencia_id = aan.id"
                + " LEFT JOIN LATERAL (SELECT COALESCE(SUM(d.kilos_netos), 0) AS kg FROM compras_cafe_seco d"
                + "  WHERE q.id IS NOT NULL AND d.agencia_id = aan.agencia_id"
                + "  AND d.numero_anuncio = cr.prefijo || '-' || aan.numero_anuncio) del ON q.id IS NOT NULL"
                + " WHERE " + AGENCY_FILTER.formatted("aan.agencia_id") + " AND (:all OR NOT aan.exportado)"
                + " ORDER BY aan.agencia_id, aan.numero_anuncio";
        return query(sql, agencyId, pendingOnly);
    }

    /** Queries "Exportado", "ExportadodeSocios", etc.: marca exactamente las filas que se exportaron. */
    public void markExported(String table, Collection<Long> ids) {
        requireAllowedTable(table, EXPORT_DATE_COLUMNS.keySet());
        if (!ids.isEmpty()) {
            jdbc.update("UPDATE " + table + " SET exportado = TRUE WHERE id IN (:ids)", new MapSqlParameterSource("ids", ids));
        }
    }

    /**
     * "Exportado Especial" (UnExportBuys, UnExportSocios, UnExportFutureBuys, UnExportExits,
     * UnExportAnuncios): vuelve a Exportado = No lo del rango de fechas, ambos extremos incluidos.
     */
    public void unmarkRange(Long agencyId, LocalDate from, LocalDate to) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("agencyId", agencyId).addValue("from", from).addValue("to", to);
        EXPORT_DATE_COLUMNS.forEach((table, dateColumn) -> jdbc.update("UPDATE " + table + " SET exportado = FALSE WHERE "
                + AGENCY_FILTER.formatted("agencia_id") + " AND " + dateColumn + " BETWEEN :from AND :to", params));
    }

    private List<ExportRow> query(String sql, Long agencyId, boolean pendingOnly) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("agencyId", agencyId)
                .addValue("all", !pendingOnly);
        return jdbc.query(sql, params, (rs, i) -> toRow(rs));
    }

    private static ExportRow toRow(ResultSet rs) throws SQLException {
        int count = rs.getMetaData().getColumnCount();
        List<Object> values = new ArrayList<>(count - 2);
        for (int c = 3; c <= count; c++) {
            Object value = rs.getObject(c);
            values.add(value instanceof Date date ? date.toLocalDate() : value);
        }
        return new ExportRow(rs.getString(1), rs.getLong(2), values);
    }
}
