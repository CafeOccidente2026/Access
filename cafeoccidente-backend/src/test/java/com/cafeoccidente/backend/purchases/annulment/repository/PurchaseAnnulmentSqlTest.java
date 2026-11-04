package com.cafeoccidente.backend.purchases.annulment.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Deuda tecnica (S2077): el SQL de Anular pasa a constantes fijas por modulo. Debe ser exactamente el que
 * armaba antes el metodo en cada llamada (copiado aqui), asi que el comportamiento no cambia.
 */
class PurchaseAnnulmentSqlTest {

    private static String oldSelect(AnnulmentModule module) {
        return "SELECT '" + module.name() + "' AS module, p.id, p.agencia_id, a.nombre AS agency_name, rc.prefijo AS prefijo_factura,"
                + " p.numero_factura, p.fecha_compra, f.codigo AS fund_code, p.cedula, p.nombres, p.apellidos,"
                + " p.especial, p.kilos_netos, p.neto_a_pagar, p.forma_pago, p.estado, p.anulacion_bloqueada, p.anulado_en"
                + " FROM " + module.table + " p JOIN agencias a ON a.id = p.agencia_id JOIN fondos f ON f.id = p.fondo_id"
                + " LEFT JOIN registros_control rc ON rc.agencia_id = p.agencia_id AND rc.activo";
    }

    private static final String OLD_OF_AGENCY = " AND (CAST(:agencyId AS BIGINT) IS NULL OR p.agencia_id = :agencyId)";

    private static String oldUnion(Function<AnnulmentModule, String> sql) {
        return Arrays.stream(AnnulmentModule.values()).map(sql).collect(Collectors.joining(" UNION ALL "));
    }

    @Test
    void theInvoiceSearchIsTheSameSqlAsBefore() {
        assertThat(PurchaseAnnulmentRepository.BY_INVOICE).isEqualTo(
                oldUnion(m -> oldSelect(m) + " WHERE p.numero_factura = :invoice" + OLD_OF_AGENCY) + " ORDER BY agency_name, module");
    }

    /** Copia del armado viejo de neighborInvoice: con factura de partida (from) o sin ella. */
    private static String oldNeighbor(AnnulmentDirection direction, Integer from) {
        String filter = from == null || direction.comparison == null ? ""
                : " AND p.numero_factura " + direction.comparison + " :from";
        return "SELECT " + direction.aggregate + "(numero_factura) FROM ("
                + oldUnion(m -> "SELECT p.numero_factura FROM " + m.table + " p WHERE TRUE" + OLD_OF_AGENCY + filter)
                + ") f";
    }

    @Test
    void theNeighborInvoiceIsTheSameSqlAsBeforeForEveryDirection() {
        for (AnnulmentDirection direction : AnnulmentDirection.values()) {
            assertThat(PurchaseAnnulmentRepository.NEIGHBOR_FROM.get(direction)).isEqualTo(oldNeighbor(direction, 1001));
            assertThat(PurchaseAnnulmentRepository.NEIGHBOR_ANY.get(direction)).isEqualTo(oldNeighbor(direction, null));
        }
    }

    @Test
    void lockingAndAnnullingAreTheSameSqlAsBeforeForEveryModule() {
        for (AnnulmentModule module : AnnulmentModule.values()) {
            assertThat(PurchaseAnnulmentRepository.LOCK.get(module))
                    .isEqualTo(oldSelect(module) + " WHERE p.id = :id FOR UPDATE OF p");
            String zeros = module.zeroed.stream().map(c -> c + " = 0").collect(Collectors.joining(", "));
            assertThat(PurchaseAnnulmentRepository.ANNUL.get(module)).isEqualTo("UPDATE " + module.table
                    + " SET estado = 'ANULADA', anulado_en = now(), anulado_por_usuario_id = :userId, numero_cheque = NULL, "
                    + zeros + " WHERE id = :id AND estado = 'VALIDA' AND NOT anulacion_bloqueada");
        }
    }
}
