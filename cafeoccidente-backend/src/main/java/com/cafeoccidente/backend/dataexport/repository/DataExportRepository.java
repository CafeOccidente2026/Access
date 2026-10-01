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
 * FutureBuys solo traen lo registrado en la web. Las facturas ANULADAS nunca se migraron: no salen.
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
            "dry_coffee_purchase", "DRY",
            "green_coffee_purchase", "GREEN",
            "husk_purchase", "HUSK",
            "other_coffee_purchase", "OTHER",
            "ferti_futuro_purchase", "FERTI");

    /** Numero de anuncio de Access: las compras nuevas lo guardan como "SDTA-3510". */
    private static final String PLAIN_ANNOUNCEMENT = "regexp_replace(p.announcement_number, '^.*-', '')";

    /** Columnas comunes a las 5 tablas de compras; Forma_de_Pago en COMPRAS es el estado de la factura
     *  ("VALIDA"; las ANULADA no existen en el sistema nuevo) y el pago real va en FPef/FPch/FPtx/FPdat. */
    private static final Map<String, String> PURCHASE_COMMON = Map.ofEntries(
            Map.entry("Anuncio", PLAIN_ANNOUNCEMENT),
            Map.entry("Agencia", "a.access_agency_value"),
            Map.entry("Fecha_Compra", "p.purchase_date"),
            Map.entry("Fecha_Anuncio", "p.announcement_date"),
            Map.entry("Cod_Prod", "pc.code"),
            Map.entry("Fondo", "f.code"),
            Map.entry("Factura", "p.invoice_number"),
            Map.entry("Prefijo", "a.invoice_prefix"),
            Map.entry("Cedula", "p.id_number"),
            Map.entry("Kilos_Brutos", "p.gross_kg"),
            // Solo Cafe Verde llena Kilos_Verdes; en Access queda en 0 en el resto (844/844 compras de
            // El Tambo, y SumaDeKilos_Verdes = 0 en comprasTotal.XLS).
            Map.entry("Kilos_Verdes", "0"),
            Map.entry("Destare", "p.tare_kg"),
            Map.entry("Kilos_Netos", "p.net_kg"),
            Map.entry("Pr_Base_CPS", "m.base_price_load"),
            Map.entry("Vr_Inventario", "p.inventory_value"),
            Map.entry("Vr_Kilo", "p.unit_price"),
            Map.entry("Vr_Bruto", "p.gross_value"),
            Map.entry("Aporte_Socio", "p.associate_contribution"),
            Map.entry("Descuento_Coop", "p.cooperative_discount"),
            Map.entry("Retefuente", "p.withholding"),
            Map.entry("Neto_a_Pagar", "p.net_to_pay"),
            Map.entry("OtrosDescuentos", "p.other_discounts"),
            Map.entry("Forma_de_Pago", "'VALIDA'"),
            Map.entry("NumCheque", "CASE WHEN p.check_number ~ '^[0-9]{1,9}$' THEN CAST(p.check_number AS INTEGER) END"),
            Map.entry("Especial", "p.special_type"),
            Map.entry("Exportado", "p.exported"),
            Map.entry("Costos", "p.costs"),
            // COMPRAS.Fiel = RegControl.Fiel de la agencia (87304051 en las 844 compras de El Tambo).
            Map.entry("Fiel", "CASE WHEN cr.trusted_id ~ '^[0-9]{1,15}$' THEN CAST(cr.trusted_id AS NUMERIC) END"),
            Map.entry("FPef", payment("s.cash_amount", "EFECTIVO")),
            Map.entry("FPch", payment("s.check_amount", "CHEQUE")),
            Map.entry("FPtx", payment("s.transfer_amount", "TRANSFERENCIA")),
            Map.entry("FPdat", payment("s.card_amount", "DATAFONO")));

    /** Lo propio de cada formulario de compra (ver los Javadoc de cada entidad). */
    private static final Map<String, Map<String, String>> PURCHASE_SPECIFIC = Map.of(
            "dry_coffee_purchase", dryLike(),
            "other_coffee_purchase", dryLike(),
            "green_coffee_purchase", Map.of(
                    "Sacos", "p.bags_count", "Kilos_Verdes", "p.green_kg", "Pr_AlmSana", "p.healthy_unit_price",
                    "Pr_AlmDefec", "p.defective_unit_price", "Vr_Kilo_Comp", "p.comp_kg_price", "Bonificación", "p.bonus",
                    "Pr_Base_PC", "p.base_price_load", "Castigo", "p.penalty", "Descuento_Fro", "p.shrinkage_discount"),
            "husk_purchase", Map.of(
                    "Sacos", "p.bags_count", "Pr_AlmSana", "p.point_price", "W_AlmSana", "p.almond_weight",
                    "PorcAlmSana", "p.almond_percentage", "Pr_Base_CPS", "p.base_price_dry_load",
                    "Descuento_Fro", "p.shrinkage_discount"),
            "ferti_futuro_purchase", Map.ofEntries(
                    Map.entry("Sacos", "p.sacos"), Map.entry("Pr_AlmSana", "p.healthy_unit_price"),
                    Map.entry("Pr_AlmDefec", "p.defective_unit_price"), Map.entry("W_AlmSana", "p.healthy_stored_weight"),
                    Map.entry("W_AlmDefec", "p.defective_stored_weight"), Map.entry("Bonificación", "p.bonus"),
                    Map.entry("PorcAlmSana", "p.healthy_percentage"), Map.entry("PorcAlmDefec", "p.defective_percentage"),
                    Map.entry("VrIncCalidad", "p.quality_increment_rate"),
                    Map.entry("IncCalidad", "p.quality_increment_amount"), Map.entry("Castigo", "p.penalty"),
                    Map.entry("Descuento_Fro", "p.freight_discount")));

    private static final String AGENCY_FILTER = "(CAST(:agencyId AS BIGINT) IS NULL OR %s = :agencyId)";

    private final NamedParameterJdbcTemplate jdbc;

    public DataExportRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static Map<String, String> dryLike() {
        return Map.ofEntries(
                Map.entry("Sacos", "p.bags_count"), Map.entry("Pr_AlmSana", "p.healthy_unit_price"),
                Map.entry("Pr_AlmDefec", "p.defective_unit_price"), Map.entry("W_TotAlm", "p.total_stored_weight"),
                Map.entry("W_AlmSana", "p.healthy_stored_weight"), Map.entry("W_AlmDefec", "p.defective_stored_weight"),
                Map.entry("Bonificación", "p.bonus"), Map.entry("PorcAlmSana", "p.healthy_percentage"),
                Map.entry("PorcAlmDefec", "p.defective_percentage"), Map.entry("PorcMerma", "p.waste_percentage"),
                Map.entry("Pr_Base_PC", "p.base_price_load"), Map.entry("Castigo", "p.penalty"),
                Map.entry("Descuento_Fro", "p.freight_discount"));
    }

    /** Pago mixto migrado: el monto de purchase_payment_split; si no, el neto entero va a su forma de pago. */
    private static String payment(String splitColumn, String method) {
        return "CASE WHEN s.source_id IS NOT NULL THEN " + splitColumn
                + " WHEN p.payment_method = '" + method + "' THEN p.net_to_pay ELSE 0 END";
    }

    private static String purchaseSelect(String table) {
        Map<String, String> specific = PURCHASE_SPECIFIC.get(table);
        String columns = PURCHASE_COLUMNS.stream()
                // NULL tipado: en el UNION una columna puede ser numerica en un modulo y vacia en otro.
                .map(c -> specific.getOrDefault(c, PURCHASE_COMMON.getOrDefault(c, "CAST(NULL AS NUMERIC)")) + " AS \"" + c + "\"")
                .collect(Collectors.joining(", "));
        return "SELECT '" + table + "' AS source_table, p.id AS source_id, " + columns
                + " FROM " + table + " p JOIN agency a ON a.id = p.agency_id JOIN fund f ON f.id = p.fund_id"
                + " JOIN product_code pc ON pc.id = p.product_code_id"
                + " LEFT JOIN agency_announcement_number aan ON aan.agency_id = p.agency_id"
                + " AND CAST(aan.announcement_number AS TEXT) = " + PLAIN_ANNOUNCEMENT
                + " LEFT JOIN announcement m ON m.id = aan.master_announcement_id"
                + " LEFT JOIN control_record cr ON cr.agency_id = p.agency_id AND cr.active"
                + " LEFT JOIN purchase_payment_split s ON s.source = '" + PURCHASE_TABLES.get(table) + "' AND s.source_id = p.id"
                + " WHERE " + AGENCY_FILTER.formatted("p.agency_id") + " AND (:all OR NOT p.exported)";
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
        String sql = "SELECT 'grower' AS source_table, g.id AS source_id, g.id_number, g.first_name, g.second_name,"
                + " g.last_name, g.second_last_name, a.access_agency_value, g.birth_date, g.birth_place, g.marital_status,"
                + " g.coffee_id_card, g.address, g.phone, g.affiliation_date, g.act_number, g.accepted, g.eligible,"
                + " g.withdrawn, g.deceased, g.observation, g.grower_type, g.transport_company, g.vehicle_plate,"
                + " COALESCE(g.exported, FALSE), g.is_association, g.city_code, g.sex, g.country, g.postal_code, g.email"
                + " FROM grower g LEFT JOIN agency a ON a.id = g.agency_id"
                + " WHERE " + AGENCY_FILTER.formatted("g.agency_id") + " AND (:all OR NOT COALESCE(g.exported, FALSE))"
                + " ORDER BY g.id";
        return query(sql, agencyId, pendingOnly);
    }

    /** FutureBuystoExport. Kilos_Entregados = Kilos_Anunciados - Saldo_Kilos (el sistema nuevo guarda el saldo). */
    public List<ExportRow> futurePurchases(Long agencyId, boolean pendingOnly) {
        String sql = "SELECT 'future_purchase' AS source_table, p.id AS source_id, " + PLAIN_ANNOUNCEMENT + ","
                + " a.access_agency_value, p.announcement_date, p.id_number, p.announced_kg,"
                + " p.announced_kg - p.remaining_kg, p.remaining_kg, p.healthy_unit_price, p.defective_unit_price,"
                + " p.costs, p.bonus, p.quality_increment, p.base_price_load, p.special_type, p.delivery_date,"
                + " p.exported, p.finca, p.municipality, p.vereda, NULL"
                + " FROM future_purchase p JOIN agency a ON a.id = p.agency_id"
                + " WHERE " + AGENCY_FILTER.formatted("p.agency_id") + " AND (:all OR NOT p.exported)"
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
        String seller = "COALESCE(d.id_number, gr.id_number, h.id_number, o.id_number, fe.id_number)";
        String entries = "SELECT 'inventory_movement' AS source_table, im.id AS source_id, a.access_agency_value,"
                + " pc.code, im.invoice_number, im.purchase_date AS fecha, im.net_kg, im.healthy_percentage,"
                + " im.inventory_value, NULL, NULL, NULL, NULL, NULL, NULL, im.special_type, im.exported, NULL, NULL, "
                + seller
                + " FROM inventory_movement im JOIN agency a ON a.id = im.agency_id"
                + " JOIN product_code pc ON pc.id = im.product_code_id"
                + " LEFT JOIN dry_coffee_purchase d ON im.purchase_module = 'DRY_COFFEE' AND d.id = im.purchase_id"
                + " LEFT JOIN green_coffee_purchase gr ON im.purchase_module = 'GREEN_COFFEE' AND gr.id = im.purchase_id"
                + " LEFT JOIN husk_purchase h ON im.purchase_module = 'HUSK' AND h.id = im.purchase_id"
                + " LEFT JOIN other_coffee_purchase o ON im.purchase_module = 'OTHER_COFFEE' AND o.id = im.purchase_id"
                + " LEFT JOIN ferti_futuro_purchase fe ON im.purchase_module = 'FERTI_FUTURO' AND fe.id = im.purchase_id"
                + " WHERE " + AGENCY_FILTER.formatted("im.agency_id") + " AND (:all OR NOT im.exported)";
        String exits = "SELECT 'remission' AS source_table, r.id AS source_id, a.access_agency_value, pc.code, NULL,"
                + " r.remission_date AS fecha, NULL, NULL, NULL, r.display_number, rl.quantity, rl.exit_percentage,"
                + " rl.unit_value, rl.output_value, r.destination, im.special_type, r.exported, rl.sacos, rl.gross_kg,"
                + " c.id_number"
                + " FROM remission_line rl JOIN remission r ON r.id = rl.remission_id"
                + " JOIN inventory_movement im ON im.id = rl.inventory_movement_id"
                + " JOIN agency a ON a.id = r.agency_id JOIN product_code pc ON pc.id = im.product_code_id"
                + " LEFT JOIN grower c ON c.id = r.conductor_id"
                + " WHERE " + AGENCY_FILTER.formatted("r.agency_id") + " AND (:all OR NOT r.exported)";
        return query(entries + " UNION ALL " + exits + " ORDER BY fecha, source_table, source_id", agencyId, pendingOnly);
    }

    /**
     * AnunciosParaExportar: una fila de ANUNCIOS por agencia = agency_announcement_number. Los anuncios
     * migrados de Access traen Pr_AlmSana/Bonificacion/Costos congelados en el maestro; los publicados
     * en la web se calculan como AnnouncementServiceImpl.toResponse con el ControlRecord de la agencia.
     * Cupo/Entregados/Saldo solo si hay cupo asignado (AnnouncementQuotaServiceImpl: compras de Seco).
     */
    public List<ExportRow> announcements(Long agencyId, boolean pendingOnly) {
        String sql = "SELECT 'agency_announcement_number' AS source_table, aan.id AS source_id,"
                + " aan.announcement_number, a.access_agency_value, aan.assigned_at, m.base_price_load,"
                + " CASE WHEN m.healthy_unit_price IS NOT NULL THEN m.healthy_unit_price"
                + "  WHEN m.point_price IS NOT NULL THEN m.point_price"
                + "  ELSE ROUND(m.base_price_load / cr.base_load, 2) - cr.costs END,"
                + " m.defective_unit_price,"
                + " CASE WHEN m.healthy_unit_price IS NOT NULL THEN m.bonus"
                + "  ELSE COALESCE(ROUND(m.special_surcharge / cr.base_load, 2), 0) END,"
                + " CASE WHEN m.healthy_unit_price IS NOT NULL THEN m.costs ELSE cr.costs END,"
                + " m.special_type, q.assigned_quota, del.kg, q.assigned_quota - del.kg, aan.exported"
                + " FROM agency_announcement_number aan JOIN announcement m ON m.id = aan.master_announcement_id"
                + " JOIN agency a ON a.id = aan.agency_id"
                + " LEFT JOIN control_record cr ON cr.agency_id = aan.agency_id AND cr.active"
                + " LEFT JOIN announcement_quota q ON q.agency_announcement_number_id = aan.id"
                + " LEFT JOIN LATERAL (SELECT COALESCE(SUM(d.net_kg), 0) AS kg FROM dry_coffee_purchase d"
                + "  WHERE q.id IS NOT NULL AND d.agency_id = aan.agency_id"
                + "  AND d.announcement_number = cr.prefix || '-' || aan.announcement_number) del ON q.id IS NOT NULL"
                + " WHERE " + AGENCY_FILTER.formatted("aan.agency_id") + " AND (:all OR NOT aan.exported)"
                + " ORDER BY aan.agency_id, aan.announcement_number";
        return query(sql, agencyId, pendingOnly);
    }

    /** Queries "Exportado", "ExportadodeSocios", etc.: marca exactamente las filas que se exportaron. */
    public void markExported(String table, Collection<Long> ids) {
        if (!ids.isEmpty()) {
            jdbc.update("UPDATE " + table + " SET exported = TRUE WHERE id IN (:ids)", new MapSqlParameterSource("ids", ids));
        }
    }

    /**
     * "Exportado Especial" (UnExportBuys, UnExportSocios, UnExportFutureBuys, UnExportExits,
     * UnExportAnuncios): vuelve a Exportado = No lo del rango de fechas, ambos extremos incluidos.
     */
    public void unmarkRange(Long agencyId, LocalDate from, LocalDate to) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("agencyId", agencyId).addValue("from", from).addValue("to", to);
        Map<String, String[]> targets = new LinkedHashMap<>();
        PURCHASE_TABLES.keySet().forEach(t -> targets.put(t, new String[] {"agency_id", "purchase_date"}));
        targets.put("grower", new String[] {"agency_id", "affiliation_date"});
        targets.put("future_purchase", new String[] {"agency_id", "announcement_date"});
        targets.put("inventory_movement", new String[] {"agency_id", "purchase_date"});
        targets.put("remission", new String[] {"agency_id", "remission_date"});
        targets.put("agency_announcement_number", new String[] {"agency_id", "assigned_at"});
        targets.forEach((table, columns) -> jdbc.update("UPDATE " + table + " SET exported = FALSE WHERE "
                + AGENCY_FILTER.formatted(columns[0]) + " AND " + columns[1] + " BETWEEN :from AND :to", params));
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
