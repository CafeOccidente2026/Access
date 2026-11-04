package com.cafeoccidente.backend.purchases.remittance.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.common.security.Roles;
import com.cafeoccidente.backend.common.security.SecurityUser;
import com.cafeoccidente.backend.purchases.annulment.repository.AnnulmentModule;
import com.cafeoccidente.backend.purchases.drycoffee.dto.DryCoffeePurchaseRequest;
import com.cafeoccidente.backend.purchases.drycoffee.service.DryCoffeePurchaseService;
import com.cafeoccidente.backend.purchases.remittance.dto.RemittancePurchaseRef;
import com.cafeoccidente.backend.purchases.remittance.dto.RemittanceRequest;
import com.cafeoccidente.backend.purchases.remittance.service.RemittanceService;
import com.cafeoccidente.backend.purchases.shared.dto.PurchasePaymentRequest;
import com.cafeoccidente.backend.purchases.shared.repository.AgencyRepository;
import com.cafeoccidente.backend.users.entity.User;
import com.cafeoccidente.backend.users.repository.RoleRepository;
import com.cafeoccidente.backend.users.repository.UserRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 7b, el riesgo mas importante: mandar una factura dos veces a Operaciones. Dos remesas al mismo tiempo con la
 * misma factura, con transacciones reales: la segunda espera el bloqueo de la primera y falla, y la factura
 * queda en una sola remesa. Al final borra lo que guardo y deja los consecutivos como estaban.
 */
@SpringBootTest
@SuppressWarnings("null")
class RemittanceConcurrencyTest {

    private static final String SELLER = "990007003";
    private static final long AGENCY = 1L;

    @Autowired
    private RemittanceService remittances;
    @Autowired
    private DryCoffeePurchaseService dry;
    @Autowired
    private UserRepository users;
    @Autowired
    private RoleRepository roles;
    @Autowired
    private AgencyRepository agencies;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private TransactionTemplate transaction;

    private final List<Runnable> cleanup = new ArrayList<>();
    private SecurityUser principal;
    private Long rp;

    @BeforeEach
    void committedPurchase() {
        User user = new User();
        user.setUsername("prueba_7b_hilos");
        user.setPasswordHash("x");
        user.setRole(roles.findByName(Roles.USUARIO).orElseThrow());
        user.setAgency(agencies.findById(AGENCY).orElseThrow());
        User saved = users.save(user);
        cleanup.add(() -> jdbc.update("DELETE FROM usuarios WHERE id = ?", saved.getId()));
        principal = new SecurityUser(saved);
        signIn();

        rp = jdbc.queryForObject("SELECT id FROM fondos WHERE codigo = 'RP'", Long.class);
        restoreConsecutives();
        Long announcement = jdbc.queryForObject("INSERT INTO anuncios (fecha_anuncio, pr_base_cps, pr_alm_defec,"
                + " sobrepr_carga_esp, pr_inc_calidad, fondo_id, especial, activo) VALUES (CURRENT_DATE, 1546900, 6000, 80000,"
                + " 50, ?, 'RN', true) RETURNING id", Long.class, rp);
        jdbc.update("INSERT INTO anuncios_agencias (agencia_id, anuncio_id, numero_anuncio, fecha_asignacion)"
                + " SELECT ?, ?, COALESCE(MAX(numero_anuncio), 0) + 1, CURRENT_DATE FROM anuncios_agencias WHERE agencia_id = ?",
                AGENCY, announcement, AGENCY);
        cleanup.add(() -> jdbc.update("DELETE FROM anuncios WHERE id = ?", announcement));
        cleanup.add(() -> jdbc.update("DELETE FROM anuncios_agencias WHERE anuncio_id = ?", announcement));
        jdbc.update("INSERT INTO terceros (identificacion, primer_nombre, primer_apellido, agencia_id, direccion, telefono, tipo,"
                + " asociacion) VALUES (?, 'PRUEBA', 'HILOS', ?, 'VEREDA', '3001112233', 'C', false)", SELLER, AGENCY);
        cleanup.add(() -> jdbc.update("DELETE FROM terceros WHERE identificacion = ?", SELLER));
    }

    /** Al terminar, los consecutivos de factura y de remision vuelven al valor que tenian. */
    private void restoreConsecutives() {
        List<Integer> invoice = jdbc.queryForList("SELECT ultimo_numero FROM consecutivos_factura WHERE agencia_id = ?",
                Integer.class, AGENCY);
        List<Integer> remission = jdbc.queryForList("SELECT ultimo_consecutivo FROM consecutivos_remision"
                + " WHERE agencia_id = ? AND fondo_id = ?", Integer.class, AGENCY, rp);
        cleanup.add(() -> {
            jdbc.update("DELETE FROM consecutivos_factura WHERE agencia_id = ?", AGENCY);
            invoice.forEach(last -> jdbc.update("INSERT INTO consecutivos_factura (agencia_id, ultimo_numero) VALUES (?, ?)",
                    AGENCY, last));
            jdbc.update("DELETE FROM consecutivos_remision WHERE agencia_id = ? AND fondo_id = ?", AGENCY, rp);
            remission.forEach(last -> jdbc.update("INSERT INTO consecutivos_remision (agencia_id, fondo_id,"
                    + " ultimo_consecutivo) VALUES (?, ?, ?)", AGENCY, rp, last));
        });
    }

    @AfterEach
    void restore() {
        SecurityContextHolder.clearContext();
        for (int i = cleanup.size() - 1; i >= 0; i--) {
            cleanup.get(i).run();
        }
    }

    private void signIn() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    /** Compra de seco guardada (con commit) y lo que hay que borrar despues. */
    private Long dryPurchase() {
        DryCoffeePurchaseRequest preview = new DryCoffeePurchaseRequest(rp, "RN", SELLER, null, null, 5,
                new BigDecimal("1000"), BigDecimal.ZERO, new BigDecimal("200"), new BigDecimal("10"), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, null);
        BigDecimal net = dry.preview(preview).netToPay();
        Long id = dry.create(new DryCoffeePurchaseRequest(rp, "RN", SELLER, null, null, 5, new BigDecimal("1000"),
                BigDecimal.ZERO, new BigDecimal("200"), new BigDecimal("10"), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, new PurchasePaymentRequest(net, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null)))
                .id();
        cleanup.add(() -> {
            List<Long> remittanceIds = jdbc.queryForList("SELECT remesa_id FROM compras_cafe_seco WHERE id = ?"
                    + " AND remesa_id IS NOT NULL", Long.class, id);
            jdbc.update("UPDATE compras_cafe_seco SET remesa_id = NULL WHERE id = ?", id);
            for (Long remittance : remittanceIds) {
                jdbc.update("DELETE FROM empaques WHERE id_transaccion = (SELECT numero_remesa FROM remesas WHERE id = ?)"
                        + " AND agencia_id = ?", remittance, AGENCY);
                jdbc.update("DELETE FROM remesas WHERE id = ?", remittance);
            }
            jdbc.update("DELETE FROM formas_pago_compras WHERE origen = 'DRY' AND origen_id = ?", id);
            jdbc.update("DELETE FROM inventarios WHERE modulo_compra = 'DRY_COFFEE' AND compra_id = ?", id);
            jdbc.update("DELETE FROM compras_cafe_seco WHERE id = ?", id);
        });
        return id;
    }

    /** Corre en otro hilo con la sesion del fiel (el SecurityContext es por hilo). */
    private <T> CompletableFuture<T> async(Supplier<T> work) {
        return CompletableFuture.supplyAsync(() -> {
            signIn();
            try {
                return work.get();
            } finally {
                SecurityContextHolder.clearContext();
            }
        });
    }

    private RemittanceRequest request(Long purchase) {
        return new RemittanceRequest("RP", "RN", List.of(new RemittancePurchaseRef(AnnulmentModule.DRY, purchase)), 10,
                "USADO");
    }

    @Test
    void twoRemittancesAtTheSameTimeWithTheSameInvoiceSendItOnlyOnce() throws Exception {
        Long purchase = dryPurchase();
        CountDownLatch firstMarked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        // La primera marca la factura y no termina; la segunda debe esperar su commit y luego fallar.
        CompletableFuture<String> first = async(() -> transaction.execute(status -> {
            String number = remittances.create(request(purchase)).number();
            firstMarked.countDown();
            await(release);
            return number;
        }));
        assertThat(firstMarked.await(10, TimeUnit.SECONDS)).isTrue();
        CompletableFuture<String> second = async(() -> remittances.create(request(purchase)).number());
        Thread.sleep(300);
        assertThat(second).isNotDone();
        release.countDown();

        String number = first.get(10, TimeUnit.SECONDS);
        assertThat(second).failsWithin(10, TimeUnit.SECONDS).withThrowableOfType(ExecutionException.class)
                .withCauseInstanceOf(BusinessRuleException.class);
        assertThat(jdbc.queryForObject("SELECT r.numero_remesa FROM compras_cafe_seco p JOIN remesas r ON r.id = p.remesa_id"
                + " WHERE p.id = ?", String.class, purchase)).isEqualTo(number);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM remesas WHERE agencia_id = ? AND creado_por_usuario_id = ?",
                Integer.class, AGENCY, principal.getUserId())).isEqualTo(1);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
