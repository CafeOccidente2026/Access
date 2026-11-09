package com.cafeoccidente.backend.supplies.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * V24: en Access el Id de transaccion se digitaba libre y hay de mas de 30 caracteres (Caja Menor de Buesaco 1:
 * "12820258-5216272-1084221672-18/07/2026", 38). Las 4 tablas lo aceptan hasta 60 para migrarlo sin recortarlo.
 */
@SpringBootTest
class TransactionIdLengthIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void v24TransactionIdTakesUpTo60Characters() {
        List<Integer> lengths = jdbc.queryForList(
                "SELECT character_maximum_length FROM information_schema.columns WHERE column_name = 'id_transaccion'"
                        + " AND table_name IN ('movimientos_caja', 'suministros', 'movimientos_caja_menor', 'empaques')",
                Integer.class);

        assertThat(lengths).hasSize(4).containsOnly(60);
    }
}
