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
import java.time.LocalDate;
import java.time.ZoneId;
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
        future(agency, announcement, daysAgo, daysAgo, idNumber, kilos, saldo);
    }

    /** Registrada hace {@code createdDaysAgo} dias con un anuncio de hace {@code daysAgo}. */
    private void future(long agency, String announcement, String daysAgo, String createdDaysAgo, String idNumber, int kilos,
            int saldo) {
        jdbc.update("INSERT INTO compras_anunciadas (creado_en, agencia_id, numero_anuncio, fecha_anuncio, cedula, nombres,"
                + " tipo, direccion, especial, kilos, saldo, fecha_entrega, pr_alm_sana, pr_alm_defec, bonificacion, costos,"
                + " pr_inc_calidad, pr_base_cps, creado_por_usuario_id) VALUES (now() - interval '" + createdDaysAgo
                + " days', ?, ?, CURRENT_DATE - " + daysAgo + ","
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

        List<PendingAnnouncementRow> rows = futures.pending(null, null, true).stream()
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

    // Hallazgo #17 (16-nov): como la consulta "ANUNCIADAS HOY" de Access (Fecha_Anuncio = Date()), por defecto solo
    // las de hoy, con saldo o sin el; Desde/Hasta elige otro rango.
    @Test
    void byDefaultOnlyTodaysAnnouncementsAreListedWithOrWithoutBalance() {
        future(AGENCY, "SDBU-801", "0", "990003501", 1000, 0);
        future(AGENCY, "SDBU-790", "40", "990003503", 800, 300);
        LocalDate today = jdbc.queryForObject("SELECT CURRENT_DATE", LocalDate.class);

        assertThat(numbers(futures.pending(null, null, false))).containsExactly("SDBU-801");
        assertThat(numbers(futures.pending(today.minusDays(45), today.minusDays(30), false))).containsExactly("SDBU-790");
        assertThat(numbers(futures.pending(today.minusDays(45), today, false))).containsExactly("SDBU-801", "SDBU-790");
    }

    // R5 (17-nov): cuenta el dia en que se registro, no el del anuncio: con el anuncio de ayer (R6) sale hoy.
    @Test
    void r5ARegisteredTodayWithYesterdaysAnnouncementIsListedToday() {
        future(AGENCY, "SDBU-805", "1", "0", "990003507", 700, 700);
        future(AGENCY, "SDBU-804", "0", "3", "990003508", 600, 600);
        LocalDate today = LocalDate.now(ZoneId.of("America/Bogota"));

        assertThat(numbers(futures.pending(null, null, false))).containsExactly("SDBU-805");
        assertThat(numbers(futures.pending(today.minusDays(3), today.minusDays(3), false))).containsExactly("SDBU-804");
    }

    private static List<String> numbers(List<PendingAnnouncementRow> rows) {
        return rows.stream().map(PendingAnnouncementRow::announcementNumber).filter(n -> n.startsWith("SDBU-")).toList();
    }
}
