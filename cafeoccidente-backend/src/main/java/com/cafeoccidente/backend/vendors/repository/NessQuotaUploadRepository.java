package com.cafeoccidente.backend.vendors.repository;

import com.cafeoccidente.backend.vendors.dto.NessQuotaRow;
import com.cafeoccidente.backend.vendors.dto.NessQuotaUploadRecord;
import com.cafeoccidente.backend.vendors.dto.NessQuotaUploadResult;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Tabla NESS (cupos_ness) y su historial de cargas (cargas_cupos_ness, V20). */
@Repository
public class NessQuotaUploadRepository {

    private final JdbcTemplate jdbc;

    public NessQuotaUploadRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Una carga a la vez; las compras pueden seguir leyendo mientras tanto. */
    public void lockNess() {
        jdbc.execute("LOCK TABLE cupos_ness IN EXCLUSIVE MODE");
    }

    /** La tabla actual en su orden de carga, sin el ".0" del CSV de Access. */
    public List<NessQuotaRow> currentQuotas() {
        return jdbc.query("SELECT regexp_replace(trim(cedula), '\\.0+$', '') AS cedula, trim(nombres) AS nombres,"
                        + " programa, CAST(cupo AS NUMERIC) AS cupo FROM cupos_ness ORDER BY id",
                (rs, i) -> new NessQuotaRow(rs.getString("cedula"), rs.getString("nombres"), rs.getString("programa"),
                        rs.getBigDecimal("cupo")));
    }

    /** RN-28.4.5: borra todo y la llena con el archivo. */
    public void replaceAll(List<NessQuotaRow> lines) {
        jdbc.update("DELETE FROM cupos_ness");
        jdbc.batchUpdate("INSERT INTO cupos_ness (cedula, nombres, programa, cupo) VALUES (?, ?, ?, ?)", lines, 1000,
                (ps, line) -> {
                    ps.setString(1, line.idNumber());
                    ps.setString(2, line.names());
                    ps.setString(3, line.program());
                    ps.setString(4, line.quota().toPlainString());
                });
    }

    public void recordUpload(long userId, String fileName, NessQuotaUploadResult result) {
        jdbc.update("INSERT INTO cargas_cupos_ness (usuario_id, archivo, filas, agregados, cambiados, iguales, retirados)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?)", userId, fileName, result.rows(), result.added(), result.changed(),
                result.unchanged(), result.removed());
    }

    public List<NessQuotaUploadRecord> history() {
        return jdbc.query("SELECT c.id, c.fecha, u.usuario, c.archivo, c.filas, c.agregados, c.cambiados, c.iguales,"
                        + " c.retirados FROM cargas_cupos_ness c JOIN usuarios u ON u.id = c.usuario_id"
                        + " ORDER BY c.fecha DESC, c.id DESC",
                (rs, i) -> new NessQuotaUploadRecord(rs.getLong("id"), rs.getObject("fecha", OffsetDateTime.class),
                        rs.getString("usuario"), rs.getString("archivo"), rs.getInt("filas"), rs.getInt("agregados"),
                        rs.getInt("cambiados"), rs.getInt("iguales"), rs.getInt("retirados")));
    }
}
