package com.cafeoccidente.backend.purchases.report.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.common.security.Roles;
import com.cafeoccidente.backend.common.security.SecurityUser;
import com.cafeoccidente.backend.purchases.annulment.repository.AnnulmentModule;
import com.cafeoccidente.backend.purchases.annulment.service.impl.PurchaseAnnulmentServiceImpl;
import com.cafeoccidente.backend.purchases.drycoffee.dto.DryCoffeePurchaseRequest;
import com.cafeoccidente.backend.purchases.drycoffee.dto.DryCoffeePurchaseResponse;
import com.cafeoccidente.backend.purchases.drycoffee.service.DryCoffeePurchaseService;
import com.cafeoccidente.backend.purchases.report.dto.FutureBalanceRow;
import com.cafeoccidente.backend.purchases.report.dto.PurchaseReportRow;
import com.cafeoccidente.backend.purchases.report.service.PurchaseReportService;
import com.cafeoccidente.backend.purchases.shared.dto.PurchasePaymentRequest;
import com.cafeoccidente.backend.purchases.shared.repository.AgencyRepository;
import com.cafeoccidente.backend.users.entity.User;
import com.cafeoccidente.backend.users.repository.RoleRepository;
import com.cafeoccidente.backend.users.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
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
 * HU-34 (Informe Anuncios a Futuro) y HU-35 (Informe Saldo Anuncios) sobre la semilla (agencia 1, prefijo SDBU).
 * Las compras se guardan con el servicio real contra una compra anunciada del anuncio SDBU-881. Transaccional.
 */
@SpringBootTest
@Transactional
@SuppressWarnings("null")
class FutureReportsIntegrationTest {

    private static final long AGENCY = 1L;
    private static final int ANNOUNCEMENT = 881;
    private static final String SELLER = "990003600";
    private static final String OTHER = "990003601";
    private static final BigDecimal KG = new BigDecimal("1000");

    @Autowired
    private PurchaseReportService reports;
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

    private Long rp;
    private Long future;
    private final LocalDate today = LocalDate.now();

    @BeforeEach
    void aFuturePurchaseOfAnnouncement881() {
        User user = new User();
        user.setUsername("prueba_4g");
        user.setPasswordHash("x");
        user.setRole(roles.findByName(Roles.ADMINISTRADOR).orElseThrow());
        user.setAgency(agencies.findById(AGENCY).orElseThrow());
        SecurityUser principal = new SecurityUser(users.save(user));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        rp = jdbc.queryForObject("SELECT id FROM fondos WHERE codigo = 'RP'", Long.class);
        for (String seller : List.of(SELLER, OTHER)) {
            jdbc.update("INSERT INTO terceros (identificacion, primer_nombre, primer_apellido, agencia_id, direccion,"
                    + " telefono, tipo, asociacion) VALUES (?, 'PRUEBA', 'INFORME', ?, 'VEREDA', '3001112233', 'C', false)",
                    seller, AGENCY);
        }
        future = future(SELLER, "SDBU-" + ANNOUNCEMENT, 5000, 5000);
        future(OTHER, "SDBU-" + ANNOUNCEMENT, 800, 800);
        future(OTHER, "SDBU-882", 700, 700);
    }

    private Long future(String seller, String announcement, int kilos, int saldo) {
        return jdbc.queryForObject("INSERT INTO compras_anunciadas (creado_en, agencia_id, numero_anuncio, fecha_anuncio,"
                + " cedula, nombres, apellidos, tipo, direccion, especial, kilos, saldo, fecha_entrega, pr_alm_sana,"
                + " pr_alm_defec, bonificacion, costos, pr_inc_calidad, pr_base_cps, creado_por_usuario_id) VALUES (now(), ?,"
                + " ?, CURRENT_DATE, ?, 'PRUEBA', 'INFORME', 'C', 'VEREDA', 'RN', ?, ?, CURRENT_DATE + 30, 9100, 6000, 30, 692,"
                + " 0, 1500000, 1) RETURNING id", Long.class, AGENCY, announcement, seller, kilos, saldo);
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    private DryCoffeePurchaseResponse bill() {
        BigDecimal net = dry.preview(request(null)).netToPay();
        return dry.create(request(new PurchasePaymentRequest(net, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null)));
    }

    private DryCoffeePurchaseRequest request(PurchasePaymentRequest payment) {
        return new DryCoffeePurchaseRequest(rp, "RN", SELLER, null, null, 5, KG, BigDecimal.ZERO, new BigDecimal("200"),
                new BigDecimal("10"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, payment, null, KG, future);
    }

    @Test
    void hu34ListsTheDryPurchasesOfThatAnnouncementBetweenTheDatesWithTheAnnulledInZero() {
        DryCoffeePurchaseResponse valid = bill();
        DryCoffeePurchaseResponse annulled = bill();
        annulment.annul(AGENCY, AnnulmentModule.DRY, annulled.id());

        List<PurchaseReportRow> rows = reports.futureAnnouncement(ANNOUNCEMENT, today, today);

        assertThat(rows).extracting(PurchaseReportRow::invoiceNumber)
                .containsExactly(valid.invoiceNumber(), annulled.invoiceNumber());
        assertThat(rows).allSatisfy(r -> {
            assertThat(r.module()).isEqualTo("DRY");
            assertThat(r.announcementNumber()).isEqualTo("SDBU-" + ANNOUNCEMENT);
            assertThat(r.announcementDate()).isEqualTo(today);
        });
        assertThat(rows.get(0).netKg()).isEqualByComparingTo(KG);
        assertThat(rows.get(1).netKg()).isZero();
        assertThat(rows.get(1).paymentMethod()).isEqualTo("ANULADA");
    }

    @Test
    void hu34OutsideTheDatesOrWithAnotherAnnouncementThereIsNothing() {
        bill();

        assertThat(reports.futureAnnouncement(ANNOUNCEMENT, today.minusDays(5), today.minusDays(1))).isEmpty();
        assertThat(reports.futureAnnouncement(ANNOUNCEMENT + 1, today, today)).isEmpty();
        assertThatThrownBy(() -> reports.futureAnnouncement(ANNOUNCEMENT, today, today.minusDays(1)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void hu35ListsThePeopleOfThatAnnouncementWithAnnouncedDeliveredAndBalance() {
        bill();

        List<FutureBalanceRow> rows = reports.futureBalance(ANNOUNCEMENT);

        assertThat(rows).extracting(FutureBalanceRow::idNumber).containsExactly(SELLER, OTHER);
        assertThat(rows.get(0)).satisfies(r -> {
            assertThat(r.announcedKg()).isEqualByComparingTo("5000");
            assertThat(r.deliveredKg()).isEqualByComparingTo("1000");
            assertThat(r.remainingKg()).isEqualByComparingTo("4000");
            assertThat(r.announcementNumber()).isEqualTo("SDBU-" + ANNOUNCEMENT);
            assertThat(r.agencyName()).isNotBlank();
        });
        assertThat(rows.get(1).deliveredKg()).isZero();
    }
}
