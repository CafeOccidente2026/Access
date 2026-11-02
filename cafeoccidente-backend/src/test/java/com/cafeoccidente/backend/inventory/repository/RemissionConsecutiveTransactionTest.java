package com.cafeoccidente.backend.inventory.repository;

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
 * 7a: el consecutivo de remision con transacciones reales (commit y rollback), que un test @Transactional
 * no puede ver. Deja la fila de consecutivos_remision de la agencia y el fondo como estaba.
 */
@SpringBootTest
@SuppressWarnings("null")
class RemissionConsecutiveTransactionTest {

    private static final long AGENCY = 1L;

    @Autowired
    private RemissionConsecutiveRepository consecutives;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private TransactionTemplate transaction;

    private Long rp;
    private List<Integer> original;

    @BeforeEach
    void rememberTheConsecutive() {
        rp = jdbc.queryForObject("SELECT id FROM fondos WHERE codigo = 'RP'", Long.class);
        original = jdbc.queryForList("SELECT ultimo_consecutivo FROM consecutivos_remision"
                + " WHERE agencia_id = ? AND fondo_id = ?", Integer.class, AGENCY, rp);
    }

    @AfterEach
    void restoreTheConsecutive() {
        jdbc.update("DELETE FROM consecutivos_remision WHERE agencia_id = ? AND fondo_id = ?", AGENCY, rp);
        original.forEach(last -> jdbc.update("INSERT INTO consecutivos_remision (agencia_id, fondo_id,"
                + " ultimo_consecutivo) VALUES (?, ?, ?)", AGENCY, rp, last));
    }

    private int takeAndCommit() {
        return transaction.execute(status -> consecutives.take(AGENCY, rp));
    }

    @Test
    void anExitAndARemittanceSavedAtTheSameTimeGetDifferentNumbers() throws Exception {
        int next = (original.isEmpty() ? 0 : original.get(0)) + 1;
        CountDownLatch firstTook = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        // El primero toma el numero y no termina; el segundo debe esperar su commit, no repetir el numero.
        CompletableFuture<Integer> first = CompletableFuture.supplyAsync(() -> transaction.execute(status -> {
            int taken = consecutives.take(AGENCY, rp);
            firstTook.countDown();
            await(release);
            return taken;
        }));
        assertThat(firstTook.await(10, TimeUnit.SECONDS)).isTrue();
        CompletableFuture<Integer> second = CompletableFuture.supplyAsync(this::takeAndCommit);
        Thread.sleep(300);
        assertThat(second).isNotDone();
        release.countDown();

        assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                .containsExactly(next, next + 1);
    }

    @Test
    void aSaveThatFailsDoesNotConsumeItsNumber() {
        int taken = transaction.execute(status -> {
            int number = consecutives.take(AGENCY, rp);
            status.setRollbackOnly();
            return number;
        });

        assertThat(takeAndCommit()).isEqualTo(taken);
    }

    @Test
    void theNumberIsOnlyTakenInsideTheSaveTransaction() {
        assertThatThrownBy(() -> consecutives.take(AGENCY, rp)).isInstanceOf(IllegalTransactionStateException.class);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
