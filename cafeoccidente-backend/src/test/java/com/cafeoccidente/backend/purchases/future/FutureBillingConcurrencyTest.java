package com.cafeoccidente.backend.purchases.future;

import static org.assertj.core.api.Assertions.assertThat;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.common.security.Roles;
import com.cafeoccidente.backend.common.security.SecurityUser;
import com.cafeoccidente.backend.purchases.drycoffee.dto.DryCoffeePurchaseRequest;
import com.cafeoccidente.backend.purchases.drycoffee.service.DryCoffeePurchaseService;
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
 * HU-33, el riesgo mas importante: dos facturas al mismo tiempo contra la misma compra anunciada. Saldo 1500 y dos
 * compras de 1000 kg: la segunda espera el bloqueo de la compra anunciada, ve el saldo ya descontado y falla.
 * Transacciones reales; al final borra por id lo que creo y deja el consecutivo de factura como estaba.
 */
@SpringBootTest
@SuppressWarnings("null")
class FutureBillingConcurrencyTest {

    private static final String SELLER = "990003302";
    private static final long AGENCY = 1L;
    private static final BigDecimal KG = new BigDecimal("1000");

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
    private Long future;

    @BeforeEach
    void committedFuturePurchase() {
        User user = new User();
        user.setUsername("prueba_4d_hilos");
        user.setPasswordHash("x");
        user.setRole(roles.findByName(Roles.USUARIO).orElseThrow());
        user.setAgency(agencies.findById(AGENCY).orElseThrow());
        User saved = users.save(user);
        cleanup.add(() -> jdbc.update("DELETE FROM usuarios WHERE id = ?", saved.getId()));
        principal = new SecurityUser(saved);
        signIn();

        rp = jdbc.queryForObject("SELECT id FROM fondos WHERE codigo = 'RP'", Long.class);
        List<Integer> invoice = jdbc.queryForList("SELECT ultimo_numero FROM consecutivos_factura WHERE agencia_id = ?",
                Integer.class, AGENCY);
        cleanup.add(() -> {
            jdbc.update("DELETE FROM consecutivos_factura WHERE agencia_id = ?", AGENCY);
            invoice.forEach(last -> jdbc.update("INSERT INTO consecutivos_factura (agencia_id, ultimo_numero) VALUES (?, ?)",
                    AGENCY, last));
        });
        jdbc.update("INSERT INTO terceros (identificacion, primer_nombre, primer_apellido, agencia_id, direccion, telefono,"
                + " tipo, asociacion) VALUES (?, 'PRUEBA', 'HILOS', ?, 'VEREDA', '3001112233', 'C', false)", SELLER, AGENCY);
        cleanup.add(() -> jdbc.update("DELETE FROM terceros WHERE identificacion = ?", SELLER));
        future = jdbc.queryForObject("INSERT INTO compras_anunciadas (creado_en, agencia_id, numero_anuncio, fecha_anuncio,"
                + " cedula, nombres, tipo, direccion, especial, kilos, saldo, fecha_entrega, pr_alm_sana, pr_alm_defec,"
                + " bonificacion, costos, pr_inc_calidad, pr_base_cps, creado_por_usuario_id) VALUES (now(), ?, 'SDBU-778',"
                + " CURRENT_DATE, ?, 'PRUEBA', 'C', 'VEREDA', 'RN', 1500, 1500, CURRENT_DATE + 30, 9100, 6000, 30, 692, 0,"
                + " 1500000, 1) RETURNING id", Long.class, AGENCY, SELLER);
        Long futureId = future;
        cleanup.add(() -> jdbc.update("DELETE FROM compras_anunciadas WHERE id = ?", futureId));
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

    private DryCoffeePurchaseRequest request(PurchasePaymentRequest payment) {
        return new DryCoffeePurchaseRequest(rp, "RN", SELLER, null, null, 5, KG, BigDecimal.ZERO, new BigDecimal("200"),
                new BigDecimal("10"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, payment, null, KG, future);
    }

    /** Factura y anota como borrarla por id. */
    private Long bill() {
        BigDecimal net = dry.preview(request(null)).netToPay();
        Long id = dry.create(request(new PurchasePaymentRequest(net, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                null))).id();
        cleanup.add(() -> {
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

    @Test
    void twoInvoicesAtTheSameTimeDoNotGoOverTheBalance() throws Exception {
        CountDownLatch firstSaved = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        // La primera factura y no termina; la segunda debe esperar el bloqueo de la compra anunciada y luego fallar.
        CompletableFuture<Long> first = async(() -> transaction.execute(status -> {
            Long id = bill();
            firstSaved.countDown();
            await(release);
            return id;
        }));
        assertThat(firstSaved.await(10, TimeUnit.SECONDS)).isTrue();
        CompletableFuture<Long> second = async(this::bill);
        Thread.sleep(300);
        assertThat(second).isNotDone();
        release.countDown();

        assertThat(first.get(10, TimeUnit.SECONDS)).isNotNull();
        assertThat(second).failsWithin(10, TimeUnit.SECONDS).withThrowableOfType(ExecutionException.class)
                .withCauseInstanceOf(BusinessRuleException.class);
        assertThat(jdbc.queryForObject("SELECT saldo FROM compras_anunciadas WHERE id = ?", BigDecimal.class, future))
                .isEqualByComparingTo("500");
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
