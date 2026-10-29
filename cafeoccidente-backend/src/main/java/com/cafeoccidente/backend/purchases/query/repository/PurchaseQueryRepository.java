package com.cafeoccidente.backend.purchases.query.repository;

import com.cafeoccidente.backend.purchases.annulment.repository.AnnulmentModule;
import com.cafeoccidente.backend.purchases.shared.dto.PurchasePaymentRequest;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Lo que "Consultar Compras" muestra ademas de la compra: agencia, ESTADO, formas de pago y remision. */
@Repository
public class PurchaseQueryRepository {

    /** Agencia y ESTADO de una compra; vacio si no existe. */
    public record Header(Long agencyId, String status) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    public PurchaseQueryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Header> header(AnnulmentModule module, Long id) {
        return jdbc.query("SELECT agencia_id, estado FROM " + module.table() + " WHERE id = :id",
                new MapSqlParameterSource("id", id), (rs, i) -> new Header(rs.getLong("agencia_id"), rs.getString("estado")))
                .stream().findFirst();
    }

    public Optional<PurchasePaymentRequest> payment(AnnulmentModule module, Long id) {
        return jdbc.query("SELECT efectivo, cheque, transferencia, datafono, numero_cheque FROM formas_pago_compras"
                        + " WHERE origen = :module AND origen_id = :id",
                new MapSqlParameterSource().addValue("module", module.name()).addValue("id", id),
                (rs, i) -> new PurchasePaymentRequest(rs.getBigDecimal("efectivo"), rs.getBigDecimal("cheque"),
                        rs.getBigDecimal("transferencia"), rs.getBigDecimal("datafono"), rs.getString("numero_cheque")))
                .stream().findFirst();
    }

    public List<String> remissions(AnnulmentModule module, Long id) {
        return jdbc.queryForList("SELECT DISTINCT r.numero_remision FROM inventarios i"
                        + " JOIN remisiones_detalle d ON d.inventario_id = i.id JOIN remisiones r ON r.id = d.remision_id"
                        + " WHERE i.modulo_compra = :module AND i.compra_id = :id ORDER BY r.numero_remision",
                new MapSqlParameterSource().addValue("module", module.inventoryModule()).addValue("id", id), String.class);
    }
}
