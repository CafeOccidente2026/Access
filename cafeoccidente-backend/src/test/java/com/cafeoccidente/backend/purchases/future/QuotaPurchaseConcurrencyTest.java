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
import java.util.Map;
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
 * HU-30, el riesgo mas importante: dos compras al mismo tiempo con el mismo anuncio con cupo. Cupo 1500 y dos
 * compras de 1000 kg de vendedores distintos (para que no las turne el bloqueo del vendedor): la segunda espera el
 * bloqueo del cupo, ve la primera ya guardada y falla. Transacciones reales; al final borra por id lo que creo y
 * deja el consecutivo de factura como estaba.
 */
@SpringBootTest
@SuppressWarnings("null")
class QuotaPurchaseConcurrencyTest {

    private static final List<String> SELLERS = List.of("990003031", "990003032");
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
    private Integer announcementNumber;

    @BeforeEach
    void committedQuotaAnnouncement() {
        User user = new User();
        user.setUsername("prueba_4b_hilos");
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
        Long announcement = jdbc.queryForObject("INSERT INTO anuncios (fecha_anuncio, pr_base_cps, pr_alm_defec,"
                + " sobrepr_carga_esp, pr_inc_calidad, fondo_id, especial, activo) VALUES (CURRENT_DATE, 1546900, 6000, 80000,"
                + " 50, ?, 'RN', true) RETURNING id", Long.class, rp);
        Map<String, Object> numbering = jdbc.queryForMap("INSERT INTO anuncios_agencias (agencia_id, anuncio_id,"
                + " numero_anuncio, fecha_asignacion) SELECT ?, ?, COALESCE(MAX(numero_anuncio), 0) + 1, CURRENT_DATE"
                + " FROM anuncios_agencias WHERE agencia_id = ? RETURNING id, numero_anuncio", AGENCY, announcement, AGENCY);
        Long numberingId = ((Number) numbering.get("id")).longValue();
        announcementNumber = ((Number) numbering.get("numero_anuncio")).intValue();
        cleanup.add(() -> jdbc.update("DELETE FROM anuncios WHERE id = ?", announcement));
        cleanup.add(() -> jdbc.update("DELETE FROM anuncios_agencias WHERE id = ?", numberingId));
        Long quota = jdbc.queryForObject("INSERT INTO cupos_anuncios (anuncio_agencia_id, cupo) VALUES (?, 1500) RETURNING id",
                Long.class, numberingId);
        cleanup.add(() -> jdbc.update("DELETE FROM cupos_anuncios WHERE id = ?", quota));
        for (String seller : SELLERS) {
            jdbc.update("INSERT INTO terceros (identificacion, primer_nombre, primer_apellido, agencia_id, direccion, telefono,"
                    + " tipo, asociacion) VALUES (?, 'PRUEBA', 'HILOS', ?, 'VEREDA', '3001112233', 'C', false)", seller, AGENCY);
            cleanup.add(() -> jdbc.update("DELETE FROM terceros WHERE identificacion = ?", seller));
        }
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

    private DryCoffeePurchaseRequest request(String seller, PurchasePaymentRequest payment) {
        return new DryCoffeePurchaseRequest(rp, "RN", seller, null, null, 5, KG, BigDecimal.ZERO, new BigDecimal("200"),
                new BigDecimal("10"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, payment, announcementNumber, KG, null);
    }

    /** Guarda la compra y anota como borrarla por id. */
    private Long buy(String seller) {
        BigDecimal net = dry.preview(request(seller, null)).netToPay();
        Long id = dry.create(request(seller, new PurchasePaymentRequest(net, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, null))).id();
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
    void twoPurchasesAtTheSameTimeDoNotGoOverTheBalance() throws Exception {
        CountDownLatch firstSaved = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        // La primera guarda y no termina; la segunda debe esperar el bloqueo del cupo y luego fallar.
        CompletableFuture<Long> first = async(() -> transaction.execute(status -> {
            Long id = buy(SELLERS.get(0));
            firstSaved.countDown();
            await(release);
            return id;
        }));
        assertThat(firstSaved.await(10, TimeUnit.SECONDS)).isTrue();
        CompletableFuture<Long> second = async(() -> buy(SELLERS.get(1)));
        Thread.sleep(300);
        assertThat(second).isNotDone();
        release.countDown();

        assertThat(first.get(10, TimeUnit.SECONDS)).isNotNull();
        assertThat(second).failsWithin(10, TimeUnit.SECONDS).withThrowableOfType(ExecutionException.class)
                .withCauseInstanceOf(BusinessRuleException.class);
        assertThat(jdbc.queryForObject("SELECT COALESCE(SUM(kilos_netos), 0) FROM compras_cafe_seco WHERE agencia_id = ?"
                + " AND numero_anuncio LIKE ?", BigDecimal.class, AGENCY, "%-" + announcementNumber))
                .isEqualByComparingTo(KG);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
