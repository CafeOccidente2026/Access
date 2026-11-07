package com.cafeoccidente.backend.purchases.future;

import static org.assertj.core.api.Assertions.assertThat;

import com.cafeoccidente.backend.common.security.Roles;
import com.cafeoccidente.backend.common.security.SecurityUser;
import com.cafeoccidente.backend.purchases.annulment.repository.AnnulmentModule;
import com.cafeoccidente.backend.purchases.annulment.service.impl.PurchaseAnnulmentServiceImpl;
import com.cafeoccidente.backend.purchases.drycoffee.dto.DryCoffeePurchaseRequest;
import com.cafeoccidente.backend.purchases.drycoffee.dto.DryCoffeePurchaseResponse;
import com.cafeoccidente.backend.purchases.drycoffee.service.DryCoffeePurchaseService;
import com.cafeoccidente.backend.purchases.future.dto.FuturePurchaseRestoreRow;
import com.cafeoccidente.backend.purchases.future.service.FuturePurchaseRestoreService;
import com.cafeoccidente.backend.purchases.shared.dto.PurchasePaymentRequest;
import com.cafeoccidente.backend.purchases.shared.repository.AgencyRepository;
import com.cafeoccidente.backend.users.entity.User;
import com.cafeoccidente.backend.users.repository.RoleRepository;
import com.cafeoccidente.backend.users.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Restaurar Compras Anunciadas" (4e, P6): el saldo se recalcula desde cero, kilos anunciados menos los kilos netos de
 * las compras no anuladas que la facturaron; nunca se suma. Semilla (agencia 1), transaccional: no deja nada.
 */
@SpringBootTest
@Transactional
@SuppressWarnings("null")
class FutureRestoreIntegrationTest {

    private static final String SELLER = "990003400";
    private static final long AGENCY = 1L;
    private static final BigDecimal KG = new BigDecimal("1000");

    @Autowired
    private FuturePurchaseRestoreService restore;
    @Autowired
    private DryCoffeePurchaseService dry;
    @Autowired
    private PurchaseAnnulmentServiceImpl annulment;
    @Autowired
    private UserRepository users;
    @Autowired
    private RoleRepository roles;
    @Autowired
    private AgencyRepository agencies;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EntityManager entityManager;

    private Long rp;
    private Long future;
    private Long userId;

    @BeforeEach
    void aFuturePurchaseOf2500() {
        User user = new User();
        user.setUsername("prueba_4e_restaurar");
        user.setPasswordHash("x");
        user.setRole(roles.findByName(Roles.ADMINISTRADOR).orElseThrow());
        user.setAgency(agencies.findById(AGENCY).orElseThrow());
        SecurityUser principal = new SecurityUser(users.save(user));
        userId = principal.getUserId();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        rp = jdbc.queryForObject("SELECT id FROM fondos WHERE codigo = 'RP'", Long.class);
        jdbc.update("INSERT INTO terceros (identificacion, primer_nombre, primer_apellido, agencia_id, direccion, telefono, tipo,"
                + " asociacion) VALUES (?, 'PRUEBA', 'RESTAURAR', ?, 'VEREDA', '3001112233', 'C', false)", SELLER, AGENCY);
        future = jdbc.queryForObject("INSERT INTO compras_anunciadas (creado_en, agencia_id, numero_anuncio, fecha_anuncio,"
                + " cedula, nombres, tipo, direccion, especial, kilos, saldo, fecha_entrega, pr_alm_sana, pr_alm_defec,"
                + " bonificacion, costos, pr_inc_calidad, pr_base_cps, creado_por_usuario_id) VALUES (now(), ?, 'SDBU-779',"
                + " CURRENT_DATE, ?, 'PRUEBA', 'C', 'VEREDA', 'RN', 2500, 2500, CURRENT_DATE + 30, 9100, 6000, 30, 692, 0,"
                + " 1500000, 1) RETURNING id", Long.class, AGENCY, SELLER);
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    private DryCoffeePurchaseResponse bill() {
        DryCoffeePurchaseRequest preview = request(null);
        BigDecimal net = dry.preview(preview).netToPay();
        return dry.create(request(new PurchasePaymentRequest(net, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null)));
    }

    private DryCoffeePurchaseRequest request(PurchasePaymentRequest payment) {
        return new DryCoffeePurchaseRequest(rp, "RN", SELLER, null, null, 5, KG, BigDecimal.ZERO, new BigDecimal("200"),
                new BigDecimal("10"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, payment, null, KG, future);
    }

    /** Lo que quedo escrito en la base (el commit de un request real hace este flush). */
    private BigDecimal balance() {
        entityManager.flush();
        return jdbc.queryForObject("SELECT saldo FROM compras_anunciadas WHERE id = ?", BigDecimal.class, future);
    }

    /** Saldo dañado a mano, como lo dejaria un error; Hibernate olvida lo que tenia en memoria. */
    private void damageBalance() {
        jdbc.update("UPDATE compras_anunciadas SET saldo = 100 WHERE id = ?", future);
        entityManager.clear();
    }

    private long historyRows() {
        return jdbc.queryForObject("SELECT count(*) FROM historial_cambios WHERE entidad = 'COMPRA_ANUNCIADA'"
                + " AND accion = 'RESTAURAR' AND entidad_id = ?", Long.class, future);
    }

    @Test
    void annulAndThenRestoreDoesNotGiveTheKilosBackTwice() {
        DryCoffeePurchaseResponse p = bill();
        annulment.annul(AGENCY, AnnulmentModule.DRY, p.id());
        assertThat(balance()).isEqualByComparingTo("2500");

        restore.restore(SELLER);

        assertThat(balance()).isEqualByComparingTo("2500");
    }

    @Test
    void aWrongBalanceIsRecalculatedFromScratchAndLeftInTheHistory() {
        bill();
        damageBalance();

        List<FuturePurchaseRestoreRow> rows = restore.restore(SELLER);

        assertThat(balance()).isEqualByComparingTo("1500");
        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.announcedKg()).isEqualByComparingTo("2500");
            assertThat(row.deliveredKg()).isEqualByComparingTo("1000");
            assertThat(row.recalculatedBalance()).isEqualByComparingTo("1500");
        });
        assertThat(historyRows()).isOne();
        Map<String, Object> history = jdbc.queryForMap("SELECT usuario_id, agencia_id, antes, despues FROM historial_cambios"
                + " WHERE entidad = 'COMPRA_ANUNCIADA' AND entidad_id = ?", future);
        assertThat(((Number) history.get("usuario_id")).longValue()).isEqualTo(userId);
        assertThat(String.valueOf(history.get("antes"))).contains("100");
        assertThat(String.valueOf(history.get("despues"))).contains("1500");
    }

    @Test
    void thePreviewShowsBothBalancesAndWritesNothing() {
        bill();
        damageBalance();

        List<FuturePurchaseRestoreRow> rows = restore.preview(SELLER);

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.currentBalance()).isEqualByComparingTo("100");
            assertThat(row.recalculatedBalance()).isEqualByComparingTo("1500");
        });
        assertThat(balance()).isEqualByComparingTo("100");
        assertThat(historyRows()).isZero();
    }

    @Test
    void aCorrectBalanceIsNotTouchedNorLogged() {
        bill();

        restore.restore(SELLER);

        assertThat(balance()).isEqualByComparingTo("1500");
        assertThat(historyRows()).isZero();
    }

    @Test
    void anotherIdNumberIsNotTouched() {
        damageBalance();

        assertThat(restore.restore("990003499")).isEmpty();
        assertThat(balance()).isEqualByComparingTo("100");
    }
}
