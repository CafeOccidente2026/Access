package com.cafeoccidente.backend.purchases.future;

import static org.assertj.core.api.Assertions.assertThat;

import com.cafeoccidente.backend.common.security.Roles;
import com.cafeoccidente.backend.common.security.SecurityUser;
import com.cafeoccidente.backend.purchases.annulment.repository.AnnulmentModule;
import com.cafeoccidente.backend.purchases.annulment.service.impl.PurchaseAnnulmentServiceImpl;
import com.cafeoccidente.backend.purchases.drycoffee.dto.DryCoffeePurchaseRequest;
import com.cafeoccidente.backend.purchases.drycoffee.dto.DryCoffeePurchaseResponse;
import com.cafeoccidente.backend.purchases.drycoffee.service.DryCoffeePurchaseService;
import com.cafeoccidente.backend.purchases.future.dto.AnnouncementQuotaRequest;
import com.cafeoccidente.backend.purchases.future.dto.AnnouncementQuotaResponse;
import com.cafeoccidente.backend.purchases.future.dto.QuotaBalanceResponse;
import com.cafeoccidente.backend.purchases.future.dto.QuotaCandidateResponse;
import com.cafeoccidente.backend.purchases.future.service.AnnouncementQuotaService;
import com.cafeoccidente.backend.purchases.shared.dto.PurchasePaymentRequest;
import com.cafeoccidente.backend.purchases.shared.repository.AgencyRepository;
import com.cafeoccidente.backend.users.entity.User;
import com.cafeoccidente.backend.users.repository.RoleRepository;
import com.cafeoccidente.backend.users.repository.UserRepository;
import java.math.BigDecimal;
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
 * Prueba del 19-nov: Kilos Entregados del cupo = kilos netos de TODAS las compras validas (no anuladas) de Seco con ese
 * anuncio y especial, normales y de cupo (informativo, como Access). Kilos con Cupo = solo las de Facturar Anuncios con
 * Cupos, y Saldo = Cupo - Kilos con Cupo. Sobre la semilla (agencia 1); cada compra pesa 1000 kg netos. Transaccional.
 */
@SpringBootTest
@Transactional
@SuppressWarnings("null")
class QuotaDeliveredKgIntegrationTest {

    private static final String SELLER = "990005050";
    private static final long AGENCY = 1L;
    private static final BigDecimal KG = new BigDecimal("1000");

    @Autowired
    private DryCoffeePurchaseService dry;
    @Autowired
    private AnnouncementQuotaService quotas;
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
    private Map<String, Object> announcement;
    private Integer number;

    @BeforeEach
    void announcementOfToday() {
        User user = new User();
        user.setUsername("prueba_entregados");
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
        announcement = jdbc.queryForMap("INSERT INTO anuncios_agencias (agencia_id, anuncio_id, numero_anuncio, fecha_asignacion)"
                + " SELECT ?, ?, COALESCE(MAX(numero_anuncio), 0) + 1, CURRENT_DATE FROM anuncios_agencias"
                + " WHERE agencia_id = ? RETURNING id, numero_anuncio", AGENCY, id, AGENCY);
        number = ((Number) announcement.get("numero_anuncio")).intValue();
        jdbc.update("INSERT INTO terceros (identificacion, primer_nombre, primer_apellido, agencia_id, direccion, telefono, tipo,"
                + " asociacion) VALUES (?, 'PRUEBA', 'ENTREGADOS', ?, 'VEREDA', '3001112233', 'C', false)", SELLER, AGENCY);
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    private DryCoffeePurchaseRequest request(Integer quota, BigDecimal delivery, PurchasePaymentRequest payment) {
        return new DryCoffeePurchaseRequest(rp, "RN", SELLER, null, null, 5, KG, BigDecimal.ZERO, new BigDecimal("200"),
                new BigDecimal("10"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, payment, quota, delivery, null);
    }

    /** Compra de Café Seco con el anuncio vigente (quota null) o por Facturar Anuncios con Cupos. */
    private DryCoffeePurchaseResponse buy(Integer quota, BigDecimal delivery) {
        BigDecimal net = dry.preview(request(quota, delivery, null)).netToPay();
        return dry.create(request(quota, delivery,
                new PurchasePaymentRequest(net, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null)));
    }

    @Test
    void asignarCupoShowsTheNormalPurchasesAsDeliveredAndTheSaldoStartsEqualToTheCupo() {
        buy(null, null);
        annulment.annul(AGENCY, AnnulmentModule.DRY, buy(null, null).id());

        QuotaCandidateResponse candidate = quotas.available(AGENCY).stream()
                .filter(c -> c.announcement().announcementNumber().endsWith("-" + number)).findFirst().orElseThrow();
        assertThat(candidate.deliveredKg()).isEqualByComparingTo(KG);
        assertThat(candidate.quotaKg()).isZero();

        // El cupo puede ser menor que lo comprado sin cupo: el saldo solo lo gastan las compras de cupo.
        AnnouncementQuotaResponse assigned = quotas.assign(AGENCY, number, new AnnouncementQuotaRequest(new BigDecimal("300")));
        assertThat(assigned.deliveredKg()).isEqualByComparingTo(KG);
        assertThat(assigned.quotaKg()).isZero();
        assertThat(assigned.balance()).isEqualByComparingTo("300");
    }

    @Test
    void facturarConCuposAndRestaurarShowEveryPurchaseAsDeliveredAndOnlyTheQuotaOnesReduceTheSaldo() {
        jdbc.update("INSERT INTO cupos_anuncios (anuncio_agencia_id, cupo) VALUES (?, 2500)", announcement.get("id"));
        buy(null, null);
        buy(number, KG);

        QuotaBalanceResponse row = quotas.withBalance(AGENCY, rp, "RN").stream().findFirst().orElseThrow();
        assertThat(row.deliveredKg()).isEqualByComparingTo("2000");
        assertThat(row.quotaKg()).isEqualByComparingTo(KG);
        assertThat(row.balance()).isEqualByComparingTo("1500");

        AnnouncementQuotaResponse quota = quotas.get(AGENCY, number);
        assertThat(quota.deliveredKg()).isEqualByComparingTo("2000");
        assertThat(quota.quotaKg()).isEqualByComparingTo(KG);
        assertThat(quota.balance()).isEqualByComparingTo("1500");
        assertThat(quotas.list(AGENCY)).filteredOn(q -> q.announcementNumber().equals(number)).singleElement()
                .satisfies(q -> assertThat(q.balance()).isEqualByComparingTo("1500"));
    }
}
