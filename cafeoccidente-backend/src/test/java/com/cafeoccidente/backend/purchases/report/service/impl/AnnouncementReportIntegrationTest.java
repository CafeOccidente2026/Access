package com.cafeoccidente.backend.purchases.report.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.common.security.Roles;
import com.cafeoccidente.backend.common.security.SecurityUser;
import com.cafeoccidente.backend.purchases.drycoffee.dto.DryCoffeePurchaseRequest;
import com.cafeoccidente.backend.purchases.drycoffee.service.DryCoffeePurchaseService;
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
 * Generar Reporte Anuncio como Access (prueba del 19-nov): por numero de anuncio (de cualquier fecha) o por rango de
 * fechas, con primer nombre y primer apellido de Asociados y el Fiel del Registro de Control. Agencia 1. Transaccional.
 */
@SpringBootTest
@Transactional
@SuppressWarnings("null")
class AnnouncementReportIntegrationTest {

    private static final long AGENCY = 1L;
    private static final String SELLER = "990007171";
    private static final String TRUSTED = "990007172";
    private static final BigDecimal KG = new BigDecimal("1000");

    @Autowired
    private PurchaseReportService reports;
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

    private Long rp;
    private String prefix;
    private final LocalDate today = LocalDate.now();

    @BeforeEach
    void sellerAndTrusted() {
        User user = new User();
        user.setUsername("prueba_reporte_anuncio");
        user.setPasswordHash("x");
        user.setRole(roles.findByName(Roles.ADMINISTRADOR).orElseThrow());
        user.setAgency(agencies.findById(AGENCY).orElseThrow());
        SecurityUser principal = new SecurityUser(users.save(user));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        rp = jdbc.queryForObject("SELECT id FROM fondos WHERE codigo = 'RP'", Long.class);
        Long id = jdbc.queryForObject("INSERT INTO anuncios (fecha_anuncio, pr_base_cps, pr_alm_defec, sobrepr_carga_esp,"
                + " pr_inc_calidad, fondo_id, especial, activo) VALUES (CURRENT_DATE, 1546900, 6000, 80000, 50, ?, 'RN', true)"
                + " RETURNING id", Long.class, rp);
        jdbc.update("INSERT INTO anuncios_agencias (agencia_id, anuncio_id, numero_anuncio, fecha_asignacion)"
                + " SELECT ?, ?, COALESCE(MAX(numero_anuncio), 0) + 1, CURRENT_DATE FROM anuncios_agencias WHERE agencia_id = ?",
                AGENCY, id, AGENCY);
        jdbc.update("INSERT INTO terceros (identificacion, primer_nombre, segundo_nombre, primer_apellido, segundo_apellido,"
                + " agencia_id, direccion, telefono, tipo, asociacion) VALUES (?, 'ANA', 'MARIA', 'PEREZ', 'GOMEZ', ?, 'VEREDA',"
                + " '3001112233', 'C', false)", SELLER, AGENCY);
        jdbc.update("INSERT INTO terceros (identificacion, primer_nombre, primer_apellido, segundo_apellido, agencia_id,"
                + " direccion, telefono, tipo, asociacion) VALUES (?, 'LUIS', 'FIEL', 'ROSERO', ?, 'VEREDA', '3001112233', 'C',"
                + " false)", TRUSTED, AGENCY);
        jdbc.update("UPDATE registros_control SET cedula_fiel = ? WHERE agencia_id = ? AND activo", TRUSTED, AGENCY);
        prefix = jdbc.queryForObject("SELECT prefijo FROM registros_control WHERE agencia_id = ? AND activo", String.class, AGENCY);
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    private Long buy() {
        DryCoffeePurchaseRequest request = new DryCoffeePurchaseRequest(rp, "RN", SELLER, null, null, 5, KG, BigDecimal.ZERO,
                new BigDecimal("200"), new BigDecimal("10"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null);
        BigDecimal net = dry.preview(request).netToPay();
        return dry.create(new DryCoffeePurchaseRequest(rp, "RN", SELLER, null, null, 5, KG, BigDecimal.ZERO,
                new BigDecimal("200"), new BigDecimal("10"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new PurchasePaymentRequest(net, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null))).id();
    }

    /** Pasa la compra a otro anuncio y fecha, como si se hubiera hecho ese dia. */
    private void move(Long id, int announcement, LocalDate date) {
        jdbc.update("UPDATE compras_cafe_seco SET numero_anuncio = ?, fecha_compra = ?, fecha_anuncio = ? WHERE id = ?",
                prefix + "-" + announcement, date, date, id);
    }

    @Test
    void byAnnouncementNumberBringsItsPurchasesOfAnyDate() {
        Long old = buy();
        move(old, 990001, today.minusDays(40));
        move(buy(), 990001, today);
        move(buy(), 990002, today);

        List<PurchaseReportRow> rows = reports.announcement(990001, null, null);

        assertThat(rows).hasSize(2).allSatisfy(r -> assertThat(r.announcementNumber()).isEqualTo(prefix + "-990001"));
        assertThat(rows).extracting(PurchaseReportRow::purchaseDate).contains(today.minusDays(40), today);
        assertThat(reports.announcement(990001, today, today)).hasSize(1);
    }

    @Test
    void withoutAnnouncementItBringsEveryAnnouncementOfTheRange() {
        move(buy(), 990001, today);
        move(buy(), 990002, today);
        move(buy(), 990003, today.minusDays(3));

        assertThat(reports.announcement(null, today, today)).extracting(PurchaseReportRow::announcementNumber)
                .containsExactlyInAnyOrder(prefix + "-990001", prefix + "-990002");
    }

    @Test
    void withoutAnnouncementTheRangeIsRequiredAndDesdeCannotBeAfterHasta() {
        String message = "Indique Desde Fecha y Hasta Fecha (Desde no puede ser posterior a Hasta)";
        assertThatThrownBy(() -> reports.announcement(null, null, today))
                .isInstanceOf(BusinessRuleException.class).hasMessage(message);
        assertThatThrownBy(() -> reports.announcement(null, today, today.minusDays(1)))
                .isInstanceOf(BusinessRuleException.class).hasMessage(message);
        assertThatThrownBy(() -> reports.announcement(990001, today, today.minusDays(1)))
                .isInstanceOf(BusinessRuleException.class).hasMessage(message);
    }

    @Test
    void namesAreTheFirstNameAndFirstSurnameOfAsociadosAndItBringsTheTrustedOfTheControlRecord() {
        move(buy(), 990001, today);

        PurchaseReportRow row = reports.announcement(990001, null, null).get(0);

        assertThat(row.firstName()).isEqualTo("ANA MARIA");
        assertThat(row.growerFirstName()).isEqualTo("ANA");
        assertThat(row.growerFirstLastName()).isEqualTo("PEREZ");
        assertThat(row.trustedName()).isEqualTo("LUIS FIEL ROSERO");
        assertThat(row.status()).isEqualTo("VALIDA");
    }
}
