package com.cafeoccidente.backend.supplies.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 5a (RF §2.1): consecutivo del Id de transaccion por agencia y codigo de pantalla, con transacciones reales (commit y
 * rollback), que un test @Transactional no puede ver. Deja consecutivos_transaccion de la agencia 1 como estaba.
 */
@SpringBootTest
@SuppressWarnings("null")
class TransactionIdTransactionTest {

    private static final long AGENCY = 1L;
    private static final long OTHER_AGENCY = 4L;
    private static final String USED_SUPPLY = "5SUM";
    private static final String USED_LOAN = "5PRE";

    @Autowired
    private TransactionIdRepository ids;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private TransactionTemplate transaction;

    private List<Object[]> original;

    @BeforeEach
    void rememberTheConsecutives() {
        original = jdbc.query("SELECT agencia_id, codigo, ultimo_consecutivo FROM consecutivos_transaccion"
                + " WHERE agencia_id IN (?, ?)", (rs, i) -> new Object[] {rs.getLong(1), rs.getString(2), rs.getInt(3)},
                AGENCY, OTHER_AGENCY);
        jdbc.update("DELETE FROM consecutivos_transaccion WHERE agencia_id IN (?, ?)", AGENCY, OTHER_AGENCY);
    }

    @AfterEach
    void restoreTheConsecutives() {
        jdbc.update("DELETE FROM consecutivos_transaccion WHERE agencia_id IN (?, ?)", AGENCY, OTHER_AGENCY);
        original.forEach(row -> jdbc.update("INSERT INTO consecutivos_transaccion (agencia_id, codigo, ultimo_consecutivo)"
                + " VALUES (?, ?, ?)", row));
    }

    private int takeAndCommit(long agency, String code) {
        return transaction.execute(status -> ids.take(agency, code));
    }

    @Test
    void eachAgencyAndScreenCodeHasItsOwnConsecutive() {
        assertThat(ids.peek(AGENCY, USED_SUPPLY)).isOne();
        assertThat(takeAndCommit(AGENCY, USED_SUPPLY)).isOne();
        assertThat(takeAndCommit(AGENCY, USED_SUPPLY)).isEqualTo(2);
        assertThat(ids.peek(AGENCY, USED_SUPPLY)).isEqualTo(3);

        // RN-27.6.2: SUM y PRE del mismo tipo no se mezclan; otra agencia tampoco.
        assertThat(takeAndCommit(AGENCY, USED_LOAN)).isOne();
        assertThat(takeAndCommit(OTHER_AGENCY, USED_SUPPLY)).isOne();
    }

    @Test
    void twoSavesAtTheSameTimeGetDifferentNumbers() throws Exception {
        CountDownLatch firstTook = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        // El primero toma el numero y no termina; el segundo debe esperar su commit, no repetir el numero.
        CompletableFuture<Integer> first = CompletableFuture.supplyAsync(() -> transaction.execute(status -> {
            int taken = ids.take(AGENCY, USED_SUPPLY);
            firstTook.countDown();
            await(release);
            return taken;
        }));
        assertThat(firstTook.await(10, TimeUnit.SECONDS)).isTrue();
        CompletableFuture<Integer> second = CompletableFuture.supplyAsync(() -> takeAndCommit(AGENCY, USED_SUPPLY));
        Thread.sleep(300);
        assertThat(second).isNotDone();
        release.countDown();

        assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS))).containsExactly(1, 2);
    }

    @Test
    void aSaveThatFailsDoesNotConsumeItsNumber() {
        int taken = transaction.execute(status -> {
            int number = ids.take(AGENCY, USED_SUPPLY);
            status.setRollbackOnly();
            return number;
        });

        assertThat(takeAndCommit(AGENCY, USED_SUPPLY)).isEqualTo(taken);
    }

    @Test
    void theNumberIsOnlyTakenInsideTheSaveTransaction() {
        assertThatThrownBy(() -> ids.take(AGENCY, USED_SUPPLY)).isInstanceOf(IllegalTransactionStateException.class);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
