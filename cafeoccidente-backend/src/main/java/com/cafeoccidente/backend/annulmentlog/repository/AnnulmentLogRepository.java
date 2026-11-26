package com.cafeoccidente.backend.annulmentlog.repository;

import com.cafeoccidente.backend.annulmentlog.dto.AnnulmentLogEntry;
import com.cafeoccidente.backend.annulmentlog.dto.AnnulmentLogFilter;
import com.cafeoccidente.backend.purchases.annulment.repository.AnnulmentModule;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Tabla anulaciones con JDBC (V35): solo INSERT y SELECT; la base rechaza UPDATE, DELETE y TRUNCATE. */
@Repository
public class AnnulmentLogRepository {

    private static final TypeReference<Map<String, String>> VALUES = new TypeReference<>() { };
    private static final Map<AnnulmentModule, String> PURCHASE_TYPES = Map.of(
            AnnulmentModule.DRY, "COMPRA_SECO", AnnulmentModule.GREEN, "COMPRA_VERDE", AnnulmentModule.HUSK, "COMPRA_PASILLA");
    public static final String REMISSION_TYPE = "REMISION";

    /** La foto de una compra, una consulta fija por tabla (el SQL no se arma en tiempo de ejecucion). */
    private static final String SNAPSHOT_COLUMNS = "SELECT p.agencia_id, rc.prefijo, p.numero_factura, p.kilos_brutos,"
            + " p.kilos_netos, p.vr_bruto, p.neto_a_pagar, p.forma_pago, p.cedula, p.nombres, p.apellidos FROM ";
    private static final String SNAPSHOT_JOIN = " p LEFT JOIN registros_control rc ON rc.agencia_id = p.agencia_id"
            + " AND rc.activo WHERE p.id = :id";
    private static final Map<AnnulmentModule, String> PURCHASE_SNAPSHOT = Map.of(
            AnnulmentModule.DRY, SNAPSHOT_COLUMNS + "compras_cafe_seco" + SNAPSHOT_JOIN,
            AnnulmentModule.GREEN, SNAPSHOT_COLUMNS + "compras_cafe_verde" + SNAPSHOT_JOIN,
            AnnulmentModule.HUSK, SNAPSHOT_COLUMNS + "compras_pasilla" + SNAPSHOT_JOIN);

    /** from y to son dias completos de Colombia (la base corre en UTC); las filas de Access no tienen fecha. */
    private static final String COLOMBIAN_DAY = "CAST(n.anulado_en AT TIME ZONE 'America/Bogota' AS DATE)";
    private static final String WHERE = " WHERE (CAST(:agencyId AS BIGINT) IS NULL OR n.agencia_id = :agencyId)"
            + " AND (CAST(:type AS VARCHAR) IS NULL OR n.tipo_documento = :type)"
            + " AND (CAST(:userId AS BIGINT) IS NULL OR n.usuario_id = :userId)"
            + " AND (CAST(:from AS DATE) IS NULL OR " + COLOMBIAN_DAY + " >= CAST(:from AS DATE))"
            + " AND (CAST(:to AS DATE) IS NULL OR " + COLOMBIAN_DAY + " <= CAST(:to AS DATE))";

    private final NamedParameterJdbcTemplate jdbc;
    private final JsonMapper json;

    public AnnulmentLogRepository(NamedParameterJdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public static String purchaseType(AnnulmentModule module) {
        return PURCHASE_TYPES.get(module);
    }

    /** Una anulacion hecha en la web: con usuario, fecha (now) y motivo. */
    public void insertWeb(String type, String number, Long agencyId, Long documentId, Long userId, String reason,
            Map<String, String> values) {
        jdbc.update("INSERT INTO anulaciones (origen, tipo_documento, numero_documento, agencia_id, documento_id,"
                        + " anulado_en, usuario_id, motivo, valores) VALUES ('WEB', :type, :number, :agencyId, :documentId,"
                        + " now(), :userId, :reason, CAST(:values AS jsonb))",
                new MapSqlParameterSource().addValue("type", type).addValue("number", number)
                        .addValue("agencyId", agencyId).addValue("documentId", documentId).addValue("userId", userId)
                        .addValue("reason", reason).addValue("values", json.writeValueAsString(values)));
    }

    /**
     * La foto de una compra antes de anularla: numero con prefijo, agencia y valores. Se lee bloqueada por la
     * anulacion en curso (lockChangeable), asi que nadie la cambia entre la foto y el 0.
     */
    public DocumentSnapshot purchaseSnapshot(AnnulmentModule module, Long id) {
        return jdbc.queryForObject(PURCHASE_SNAPSHOT.get(module), new MapSqlParameterSource("id", id), (rs, i) -> {
                    Map<String, String> values = new LinkedHashMap<>();
                    put(values, "grossKg", rs.getBigDecimal("kilos_brutos"));
                    put(values, "netKg", rs.getBigDecimal("kilos_netos"));
                    put(values, "grossValue", rs.getBigDecimal("vr_bruto"));
                    put(values, "netToPay", rs.getBigDecimal("neto_a_pagar"));
                    put(values, "paymentMethod", rs.getString("forma_pago"));
                    put(values, "idNumber", rs.getString("cedula"));
                    put(values, "sellerName", joined(rs.getString("nombres"), rs.getString("apellidos")));
                    String prefix = rs.getString("prefijo");
                    String number = (prefix == null ? "" : prefix + " ") + rs.getInt("numero_factura");
                    return new DocumentSnapshot(rs.getLong("agencia_id"), number, values);
                });
    }

    /** La foto de una remision antes de anularla: kilos, valor de salida, destino, conductor, placa, fondo y facturas. */
    public DocumentSnapshot remissionSnapshot(Long id) {
        MapSqlParameterSource params = new MapSqlParameterSource("id", id);
        String invoices = String.join(", ", jdbc.queryForList("SELECT DISTINCT i.numero_factura::text"
                + " FROM remisiones_detalle d JOIN inventarios i ON i.id = d.inventario_id WHERE d.remision_id = :id"
                + " ORDER BY 1", params, String.class));
        return jdbc.queryForObject("SELECT r.agencia_id, r.numero_remision, r.destino, f.codigo AS fondo,"
                        + " t.identificacion, t.primer_nombre, t.primer_apellido, t.vehiculo,"
                        + " (SELECT sum(cantidad) FROM remisiones_detalle WHERE remision_id = r.id) AS kg,"
                        + " (SELECT sum(valor_salida) FROM remisiones_detalle WHERE remision_id = r.id) AS valor"
                        + " FROM remisiones r LEFT JOIN fondos f ON f.id = r.fondo_id"
                        + " LEFT JOIN terceros t ON t.id = r.conductor_tercero_id WHERE r.id = :id",
                params, (rs, i) -> {
                    Map<String, String> values = new LinkedHashMap<>();
                    put(values, "kg", rs.getBigDecimal("kg"));
                    put(values, "outputValue", rs.getBigDecimal("valor"));
                    put(values, "destination", rs.getString("destino"));
                    put(values, "conductorIdNumber", rs.getString("identificacion"));
                    put(values, "conductorName", joined(rs.getString("primer_nombre"), rs.getString("primer_apellido")));
                    put(values, "vehiclePlate", rs.getString("vehiculo"));
                    put(values, "fund", rs.getString("fondo"));
                    put(values, "invoices", invoices.isEmpty() ? null : invoices);
                    return new DocumentSnapshot(rs.getLong("agencia_id"), rs.getString("numero_remision"), values);
                });
    }

    public List<AnnulmentLogEntry> search(AnnulmentLogFilter filter) {
        return jdbc.query("SELECT n.id, n.origen, n.tipo_documento, n.numero_documento, a.nombre AS agencia,"
                        + " n.documento_id, n.anulado_en, u.usuario, n.motivo, n.valores, n.nota FROM anulaciones n"
                        + " JOIN agencias a ON a.id = n.agencia_id LEFT JOIN usuarios u ON u.id = n.usuario_id" + WHERE
                        + " ORDER BY n.anulado_en DESC NULLS LAST, n.id DESC LIMIT :size OFFSET :offset",
                params(filter).addValue("size", filter.size()).addValue("offset", (long) filter.page() * filter.size()),
                (rs, i) -> toEntry(rs));
    }

    public long count(AnnulmentLogFilter filter) {
        Long total = jdbc.queryForObject("SELECT count(*) FROM anulaciones n" + WHERE, params(filter), Long.class);
        return total == null ? 0 : total;
    }

    private static MapSqlParameterSource params(AnnulmentLogFilter filter) {
        return new MapSqlParameterSource().addValue("agencyId", filter.agencyId()).addValue("type", filter.documentType())
                .addValue("userId", filter.userId()).addValue("from", filter.from()).addValue("to", filter.to());
    }

    private AnnulmentLogEntry toEntry(ResultSet rs) throws SQLException {
        String values = rs.getString("valores");
        return new AnnulmentLogEntry(rs.getLong("id"), rs.getString("origen"), rs.getString("tipo_documento"),
                rs.getString("numero_documento"), rs.getString("agencia"), (Long) rs.getObject("documento_id", Long.class),
                rs.getObject("anulado_en", OffsetDateTime.class), rs.getString("usuario"), rs.getString("motivo"),
                values == null ? Map.of() : json.readValue(values, VALUES), rs.getString("nota"));
    }

    private static void put(Map<String, String> values, String key, Object value) {
        if (value != null) {
            values.put(key, value instanceof BigDecimal number ? number.toPlainString() : value.toString());
        }
    }

    private static String joined(String... parts) {
        String text = Stream.of(parts).filter(Objects::nonNull).map(String::strip).filter(s -> !s.isEmpty())
                .collect(Collectors.joining(" "));
        return text.isEmpty() ? null : text;
    }

    /** Agencia, numero con prefijo y valores de un documento en el momento de anularlo. */
    public record DocumentSnapshot(Long agencyId, String number, Map<String, String> values) {
    }
}
