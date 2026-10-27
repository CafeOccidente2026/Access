package com.cafeoccidente.backend.purchases.shared.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cafeoccidente.backend.controlrecord.entity.ControlRecord;
import com.cafeoccidente.backend.controlrecord.service.ControlRecordService;
import com.cafeoccidente.backend.purchases.shared.service.InvoiceConsecutiveService;
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
 * HU-15: el consecutivo con transacciones reales (commit y rollback), que un test @Transactional no
 * puede ver. Deja la fila de consecutivos_factura de la agencia como estaba.
 */
@SpringBootTest
@SuppressWarnings("null")
class InvoiceConsecutiveTransactionTest {

    @Autowired
    private InvoiceConsecutiveService service;
    @Autowired
    private ControlRecordService controlRecords;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private TransactionTemplate transaction;

    private Long agencyId;
    private ControlRecord record;
    private List<Integer> original;

    @BeforeEach
    void rememberTheConsecutive() {
        agencyId = jdbc.queryForObject("SELECT min(agencia_id) FROM registros_control WHERE activo", Long.class);
        record = controlRecords.getActive(agencyId);
        original = jdbc.queryForList("SELECT ultimo_numero FROM consecutivos_factura WHERE agencia_id = ?", Integer.class, agencyId);
    }

    @AfterEach
    void restoreTheConsecutive() {
        jdbc.update("DELETE FROM consecutivos_factura WHERE agencia_id = ?", agencyId);
        original.forEach(last -> jdbc.update(
                "INSERT INTO consecutivos_factura (agencia_id, ultimo_numero) VALUES (?, ?)", agencyId, last));
    }

    private int takeAndCommit() {
        return transaction.execute(status -> service.take(agencyId, record));
    }

    @Test
    void twoPurchasesSavedAtTheSameTimeGetDifferentNumbers() throws Exception {
        int next = service.next(agencyId, record).invoiceNumber();
        CountDownLatch firstTook = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        // La primera toma el numero y no termina; la segunda debe esperar su commit, no repetir el numero.
        CompletableFuture<Integer> first = CompletableFuture.supplyAsync(() -> transaction.execute(status -> {
            int taken = service.take(agencyId, record);
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
    void aPurchaseThatFailsDoesNotConsumeItsNumber() {
        int next = service.next(agencyId, record).invoiceNumber();

        transaction.executeWithoutResult(status -> {
            assertThat(service.take(agencyId, record)).isEqualTo(next);
            status.setRollbackOnly();
        });

        assertThat(takeAndCommit()).isEqualTo(next);
    }

    @Test
    void theNumberIsOnlyTakenInsideTheSaveTransaction() {
        assertThatThrownBy(() -> service.take(agencyId, record)).isInstanceOf(IllegalTransactionStateException.class);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
