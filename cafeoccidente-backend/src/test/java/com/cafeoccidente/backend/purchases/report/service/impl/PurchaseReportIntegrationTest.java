package com.cafeoccidente.backend.purchases.report.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.common.security.Roles;
import com.cafeoccidente.backend.common.security.SecurityUser;
import com.cafeoccidente.backend.purchases.annulment.repository.AnnulmentModule;
import com.cafeoccidente.backend.purchases.annulment.service.PurchaseAnnulmentService;
import com.cafeoccidente.backend.purchases.drycoffee.dto.DryCoffeePurchaseRequest;
import com.cafeoccidente.backend.purchases.drycoffee.service.DryCoffeePurchaseService;
import com.cafeoccidente.backend.purchases.greencoffee.dto.GreenCoffeePurchaseRequest;
import com.cafeoccidente.backend.purchases.greencoffee.service.GreenCoffeePurchaseService;
import com.cafeoccidente.backend.purchases.husk.dto.HuskPurchaseRequest;
import com.cafeoccidente.backend.purchases.husk.service.HuskPurchaseService;
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
 * 6g (HU-26 / RF-24, HU-27 / RF-25): las compras de la agencia de trabajo entre dos fechas, de los 3
 * modulos como en Access (todos estaban en COMPRAS), anuladas incluidas con valores en 0. Sobre la
 * semilla (agencia 1), asi corre en CI. Transaccional: no deja nada.
 */
@SpringBootTest
@Transactional
@SuppressWarnings("null")
class PurchaseReportIntegrationTest {

    private static final String SELLER = "990000888";
    private static final long AGENCY = 1L;

    @Autowired
    private PurchaseReportService reports;
    @Autowired
    private DryCoffeePurchaseService dry;
    @Autowired
    private GreenCoffeePurchaseService green;
    @Autowired
    private HuskPurchaseService husk;
    @Autowired
    private PurchaseAnnulmentService annulment;
    @Autowired
    private UserRepository users;
    @Autowired
    private RoleRepository roles;
    @Autowired
    private AgencyRepository agencies;
    @Autowired
    private JdbcTemplate jdbc;

    private Long rp;

    @BeforeEach
    void pointWithAnnouncementsAndASeller() {
        User user = new User();
        user.setUsername("prueba_6g");
        user.setPasswordHash("x");
        user.setRole(roles.findByName(Roles.ADMINISTRADOR).orElseThrow());
        user.setAgency(agencies.findById(AGENCY).orElseThrow());
        SecurityUser principal = new SecurityUser(users.save(user));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        rp = jdbc.queryForObject("SELECT id FROM fondos WHERE codigo = 'RP'", Long.class);
        for (String special : new String[] {"RN", "CV", "PASILLA"}) {
            Long id = jdbc.queryForObject("INSERT INTO anuncios (fecha_anuncio, pr_base_cps, pr_alm_defec, sobrepr_carga_esp,"
                    + " pr_inc_calidad, fondo_id, especial, activo) VALUES (CURRENT_DATE, 1546900, 6000, 80000, 50, ?, ?, true)"
                    + " RETURNING id", Long.class, rp, special);
            jdbc.update("INSERT INTO anuncios_agencias (agencia_id, anuncio_id, numero_anuncio, fecha_asignacion)"
                    + " SELECT ?, ?, COALESCE(MAX(numero_anuncio), 0) + 1, CURRENT_DATE FROM anuncios_agencias WHERE agencia_id = ?",
                    AGENCY, id, AGENCY);
        }
        jdbc.update("UPDATE registros_control SET peso_tot_alm_cv = 140, peso_tot_pasilla_cv = 12.5, peso_alm_sana_cv = 186.18"
                + " WHERE agencia_id = ? AND activo", AGENCY);
        jdbc.update("INSERT INTO terceros (identificacion, primer_nombre, primer_apellido, agencia_id, direccion, telefono, tipo,"
                + " asociacion) VALUES (?, 'PRUEBA', 'REPORTES', ?, 'VEREDA', '3001112233', 'C', false)", SELLER, AGENCY);
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    private static PurchasePaymentRequest cash(BigDecimal net) {
        return new PurchasePaymentRequest(net, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null);
    }

    private DryCoffeePurchaseRequest dryRequest(PurchasePaymentRequest payment) {
        return new DryCoffeePurchaseRequest(rp, "RN", SELLER, null, null, 5, new BigDecimal("1000"), BigDecimal.ZERO,
                new BigDecimal("200"), new BigDecimal("10"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, payment);
    }

    private GreenCoffeePurchaseRequest greenRequest(PurchasePaymentRequest payment) {
        return new GreenCoffeePurchaseRequest(rp, SELLER, null, null, 5, new BigDecimal("1000"), BigDecimal.ZERO,
                BigDecimal.ZERO, new BigDecimal("12000"), BigDecimal.ZERO, BigDecimal.ZERO, payment);
    }

    private HuskPurchaseRequest huskRequest(PurchasePaymentRequest payment) {
        return new HuskPurchaseRequest(rp, SELLER, null, null, new BigDecimal("200"), 5, new BigDecimal("1000"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, payment);
    }

    private List<PurchaseReportRow> ours(LocalDate from, LocalDate to) {
        return reports.byDate(from, to).stream().filter(r -> SELLER.equals(r.idNumber())).toList();
    }

    @Test
    void listsDryGreenAndHuskOfTheDayWithTheirAnnouncementAndAnnulledInZero() {
        var d = dry.create(dryRequest(cash(dry.preview(dryRequest(null)).netToPay())));
        var g = green.create(greenRequest(cash(green.preview(greenRequest(null)).netToPay())));
        var h = husk.create(huskRequest(cash(husk.preview(huskRequest(null)).netToPay())));
        var annulled = dry.create(dryRequest(cash(dry.preview(dryRequest(null)).netToPay())));
        annulment.annul(AGENCY, AnnulmentModule.DRY, annulled.id());
        LocalDate today = LocalDate.now();

        List<PurchaseReportRow> rows = ours(today, today);

        assertThat(rows).extracting(PurchaseReportRow::invoiceNumber)
                .containsExactly(d.invoiceNumber(), g.invoiceNumber(), h.invoiceNumber(), annulled.invoiceNumber());
        assertThat(rows).extracting(PurchaseReportRow::module).containsExactly("DRY", "GREEN", "HUSK", "DRY");
        assertThat(rows).extracting(PurchaseReportRow::specialType).containsExactly("RN", "CV", "PASILLA", "RN");
        assertThat(rows).allSatisfy(r -> {
            assertThat(r.announcementNumber()).isNotBlank();
            assertThat(r.agencyName()).isNotBlank();
            assertThat(r.prefix()).isEqualTo("SDBU");
            assertThat(r.productCode()).isNotBlank();
        });

        PurchaseReportRow seco = rows.get(0);
        assertThat(seco.netKg()).isEqualByComparingTo(d.netKg());
        assertThat(seco.netToPay()).isEqualByComparingTo(d.netToPay());
        assertThat(seco.factor()).isNotNull();
        assertThat(seco.greenKg()).as("Kilos Verdes solo aplica al verde").isNull();
        assertThat(seco.paymentMethod()).isEqualTo("EFECTIVO");
        assertThat(seco.status()).isEqualTo("VALIDA");

        assertThat(rows.get(1).greenKg()).isNotNull();
        assertThat(rows.get(2).factor()).as("en pasilla la columna Porc es el % de almendra").isNotNull();

        PurchaseReportRow zero = rows.get(3);
        assertThat(zero.status()).isEqualTo("ANULADA");
        assertThat(zero.paymentMethod()).isEqualTo("ANULADA");
        assertThat(zero.netKg()).isZero();
        assertThat(zero.netToPay()).isZero();
    }

    @Test
    void leavesOutTheAnnouncementsOfTheDayWithoutPurchases() {
        var d = dry.create(dryRequest(cash(dry.preview(dryRequest(null)).netToPay())));
        Long id = jdbc.queryForObject("INSERT INTO anuncios (fecha_anuncio, pr_base_cps, pr_alm_defec, sobrepr_carga_esp,"
                + " pr_inc_calidad, fondo_id, especial, activo) VALUES (CURRENT_DATE, 1546900, 6000, 80000, 50, ?, 'RN', true)"
                + " RETURNING id", Long.class, rp);
        Integer unused = jdbc.queryForObject("INSERT INTO anuncios_agencias (agencia_id, anuncio_id, numero_anuncio,"
                + " fecha_asignacion) SELECT ?, ?, COALESCE(MAX(numero_anuncio), 0) + 1, CURRENT_DATE FROM anuncios_agencias"
                + " WHERE agencia_id = ? RETURNING numero_anuncio", Integer.class, AGENCY, id, AGENCY);
        LocalDate today = LocalDate.now();

        List<PurchaseReportRow> rows = reports.byDate(today, today);

        // Solo la compra de seco: los anuncios de verde y pasilla del dia y el RN nuevo no tuvieron compras.
        assertThat(ours(today, today)).extracting(PurchaseReportRow::invoiceNumber).containsExactly(d.invoiceNumber());
        assertThat(rows).extracting(PurchaseReportRow::announcementNumber).doesNotContain(String.valueOf(unused));
    }

    @Test
    void onlyTheWorkingAgencyAndTheChosenDates() {
        var d = dry.create(dryRequest(cash(dry.preview(dryRequest(null)).netToPay())));
        jdbc.update("UPDATE compras_cafe_seco SET fecha_compra = fecha_compra - 3 WHERE id = ?", d.id());
        LocalDate today = LocalDate.now();

        assertThat(ours(today, today)).isEmpty();
        assertThat(ours(today.minusDays(3), today)).extracting(PurchaseReportRow::invoiceNumber).containsExactly(d.invoiceNumber());

        jdbc.update("UPDATE compras_cafe_seco SET agencia_id = (SELECT min(id) FROM agencias WHERE id <> ?) WHERE id = ?",
                AGENCY, d.id());
        assertThat(ours(today.minusDays(3), today)).isEmpty();
    }

    @Test
    void rejectsAFromDateAfterTheToDate() {
        LocalDate today = LocalDate.now();
        assertThatThrownBy(() -> reports.byDate(today, today.minusDays(1))).isInstanceOf(BusinessRuleException.class);
    }
}
