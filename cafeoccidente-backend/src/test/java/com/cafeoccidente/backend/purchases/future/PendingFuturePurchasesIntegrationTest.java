package com.cafeoccidente.backend.purchases.future;

import static org.assertj.core.api.Assertions.assertThat;

import com.cafeoccidente.backend.purchases.future.dto.PendingAnnouncementRow;
import com.cafeoccidente.backend.purchases.future.service.FuturePurchaseService;
import com.cafeoccidente.backend.common.security.Roles;
import com.cafeoccidente.backend.common.security.SecurityUser;
import com.cafeoccidente.backend.purchases.shared.repository.AgencyRepository;
import com.cafeoccidente.backend.users.entity.User;
import com.cafeoccidente.backend.users.repository.RoleRepository;
import com.cafeoccidente.backend.users.repository.UserRepository;
import java.util.List;
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
 * HU-31 "Anunciadas de Hoy" con la respuesta del area (P2): las compras a futuro con saldo de la agencia de trabajo, de
 * cualquier fecha, agrupadas por anuncio. Semilla (agencia 1), transaccional: no deja nada.
 */
@SpringBootTest
@Transactional
@SuppressWarnings("null")
class PendingFuturePurchasesIntegrationTest {

    private static final long AGENCY = 1L;

    @Autowired
    private FuturePurchaseService futures;
    @Autowired
    private UserRepository users;
    @Autowired
    private RoleRepository roles;
    @Autowired
    private AgencyRepository agencies;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void signIn() {
        User user = new User();
        user.setUsername("prueba_4f");
        user.setPasswordHash("x");
        user.setRole(roles.findByName(Roles.USUARIO).orElseThrow());
        user.setAgency(agencies.findById(AGENCY).orElseThrow());
        SecurityUser principal = new SecurityUser(users.save(user));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    private void future(long agency, String announcement, String daysAgo, String idNumber, int kilos, int saldo) {
        jdbc.update("INSERT INTO compras_anunciadas (creado_en, agencia_id, numero_anuncio, fecha_anuncio, cedula, nombres,"
                + " tipo, direccion, especial, kilos, saldo, fecha_entrega, pr_alm_sana, pr_alm_defec, bonificacion, costos,"
                + " pr_inc_calidad, pr_base_cps, creado_por_usuario_id) VALUES (now(), ?, ?, CURRENT_DATE - " + daysAgo + ","
                + " ?, 'PRUEBA', 'C', 'VEREDA', 'RN', ?, ?, CURRENT_DATE + 30, 9100, 6000, 30, 692, 0, 1500000, 1)",
                agency, announcement, idNumber, kilos, saldo);
    }

    @Test
    void ca1ListsTheFuturePurchasesWithBalanceOfAnyDateGroupedByAnnouncement() {
        future(AGENCY, "SDBU-801", "0", "990003501", 1000, 1000);
        future(AGENCY, "SDBU-801", "0", "990003502", 500, 200);
        future(AGENCY, "SDBU-790", "40", "990003503", 800, 300);
        future(AGENCY, "SDBU-790", "40", "990003504", 600, 0);
        future(AGENCY, "SDBU-780", "60", "990003505", 400, 0);
        future(2L, "SDB2-801", "0", "990003506", 900, 900);

        List<PendingAnnouncementRow> rows = futures.pending().stream()
                .filter(r -> r.announcementNumber().startsWith("SDBU-8") || r.announcementNumber().startsWith("SDBU-7"))
                .toList();

        assertThat(rows).extracting(PendingAnnouncementRow::announcementNumber).containsExactly("SDBU-801", "SDBU-790");
        assertThat(rows.get(0)).satisfies(r -> {
            assertThat(r.announcedKg()).isEqualByComparingTo("1500");
            assertThat(r.remainingKg()).isEqualByComparingTo("1200");
            assertThat(r.growers()).isEqualTo(2);
            assertThat(r.specialType()).isEqualTo("RN");
            assertThat(r.healthyUnitPrice()).isEqualByComparingTo("9100");
        });
        // Una vieja con saldo sigue apareciendo (no vence por fecha); en la suma solo cuentan las que tienen saldo.
        assertThat(rows.get(1)).satisfies(r -> {
            assertThat(r.announcedKg()).isEqualByComparingTo("800");
            assertThat(r.remainingKg()).isEqualByComparingTo("300");
            assertThat(r.growers()).isEqualTo(1);
        });
    }
}
