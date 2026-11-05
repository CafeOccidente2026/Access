package com.cafeoccidente.backend.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.cafeoccidente.backend.common.security.Roles;
import com.cafeoccidente.backend.common.security.SecurityUser;
import com.cafeoccidente.backend.controlrecord.service.ControlRecordService;
import com.cafeoccidente.backend.purchases.drycoffee.dto.DryCoffeePurchaseRequest;
import com.cafeoccidente.backend.purchases.drycoffee.service.DryCoffeePurchaseService;
import com.cafeoccidente.backend.purchases.shared.dto.PurchasePaymentRequest;
import com.cafeoccidente.backend.purchases.shared.repository.AgencyRepository;
import com.cafeoccidente.backend.purchases.shared.service.PurchaseContextService;
import com.cafeoccidente.backend.purchases.shared.service.impl.InvoiceConsecutiveServiceImpl;
import com.cafeoccidente.backend.users.entity.User;
import com.cafeoccidente.backend.users.repository.RoleRepository;
import com.cafeoccidente.backend.users.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * El servidor y la base corren en UTC: a las 20:00 de Colombia ya es el dia siguiente en UTC. La fecha del negocio sale
 * del Clock de America/Bogota. El reloj fijo es de un dia distinto al de hoy, asi el test falla tanto con UTC como con el
 * reloj de la maquina.
 */
@SpringBootTest
@Transactional
@SuppressWarnings("null")
class ColombiaClockIntegrationTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    /** 2026-01-14 20:00 en Colombia = 2026-01-15 01:00 UTC. */
    private static final Instant EIGHT_PM = Instant.parse("2026-01-15T01:00:00Z");
    private static final LocalDate COLOMBIAN_DAY = LocalDate.of(2026, 1, 14);
    private static final String SELLER = "990008001";
    private static final long AGENCY = 1L;

    @MockitoBean
    private Clock clock;
    @Autowired
    private DryCoffeePurchaseService dry;
    @Autowired
    private PurchaseContextService contexts;
    @Autowired
    private InvoiceConsecutiveServiceImpl invoices;
    @Autowired
    private ControlRecordService controlRecords;
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
    void eightPmInColombia() {
        when(clock.instant()).thenReturn(EIGHT_PM);
        when(clock.getZone()).thenReturn(BOGOTA);

        User user = new User();
        user.setUsername("prueba_reloj");
        user.setPasswordHash("x");
        user.setRole(roles.findByName(Roles.USUARIO).orElseThrow());
        user.setAgency(agencies.findById(AGENCY).orElseThrow());
        SecurityUser principal = new SecurityUser(users.save(user));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        rp = jdbc.queryForObject("SELECT id FROM fondos WHERE codigo = 'RP'", Long.class);
        Long announcement = jdbc.queryForObject("INSERT INTO anuncios (fecha_anuncio, pr_base_cps, pr_alm_defec,"
                + " sobrepr_carga_esp, pr_inc_calidad, fondo_id, especial, activo) VALUES (?, 1546900, 6000, 80000, 50, ?,"
                + " 'RN', true) RETURNING id", Long.class, COLOMBIAN_DAY, rp);
        jdbc.update("INSERT INTO anuncios_agencias (agencia_id, anuncio_id, numero_anuncio, fecha_asignacion)"
                + " SELECT ?, ?, COALESCE(MAX(numero_anuncio), 0) + 1, ? FROM anuncios_agencias WHERE agencia_id = ?",
                AGENCY, announcement, COLOMBIAN_DAY, AGENCY);
        jdbc.update("INSERT INTO terceros (identificacion, primer_nombre, primer_apellido, agencia_id, direccion, telefono,"
                + " tipo, asociacion) VALUES (?, 'PRUEBA', 'RELOJ', ?, 'VEREDA', '3001112233', 'C', false)", SELLER, AGENCY);
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    private DryCoffeePurchaseRequest request(PurchasePaymentRequest payment) {
        return new DryCoffeePurchaseRequest(rp, "RN", SELLER, null, null, 5, new BigDecimal("1000"), BigDecimal.ZERO,
                new BigDecimal("200"), new BigDecimal("10"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, payment);
    }

    private long buyAtEightPm() {
        BigDecimal net = dry.preview(request(null)).netToPay();
        return dry.create(request(new PurchasePaymentRequest(net, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null)))
                .id();
    }

    @Test
    void aDryPurchaseAtEightPmKeepsTheColombianDay() {
        long id = buyAtEightPm();

        assertThat(jdbc.queryForObject("SELECT fecha_compra FROM compras_cafe_seco WHERE id = ?", LocalDate.class, id))
                .isEqualTo(COLOMBIAN_DAY);
    }

    @Test
    void theDailyWithholdingTotalAddsTheSameColombianDay() {
        buyAtEightPm();
        BigDecimal soldThatDay = jdbc.queryForObject("SELECT COALESCE(SUM(vr_bruto), 0) FROM compras_cafe_seco"
                + " WHERE cedula = ? AND fecha_compra = ?", BigDecimal.class, SELLER, COLOMBIAN_DAY);

        // Q10: lo vendido ese dia de Colombia suma para la retencion de la compra siguiente.
        assertThat(soldThatDay).isPositive();
        assertThat(contexts.forPreview(rp, "RN", SELLER, null, null).dailyTotals().grossValue())
                .isEqualByComparingTo(soldThatDay);
    }

    @Test
    void aResolutionThatExpiresTodayIsStillValidAtEightPm() {
        // Vence el mismo dia de Colombia: en UTC ya seria el dia siguiente y saldria "vencida".
        jdbc.update("UPDATE registros_control SET fecha_resolucion = ?, vigencia = 0 WHERE agencia_id = ? AND activo",
                COLOMBIAN_DAY, AGENCY);

        assertThat(invoices.next(AGENCY, controlRecords.getActive(AGENCY)).warning())
                .isNotEqualTo("La resolución de facturación está vencida");
    }
}
