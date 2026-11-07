package com.cafeoccidente.backend.purchases.future;

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
import com.cafeoccidente.backend.purchases.future.dto.QuotaBalanceResponse;
import com.cafeoccidente.backend.purchases.future.service.AnnouncementQuotaService;
import com.cafeoccidente.backend.purchases.shared.dto.PurchasePaymentRequest;
import com.cafeoccidente.backend.purchases.shared.repository.AgencyRepository;
import com.cafeoccidente.backend.users.entity.User;
import com.cafeoccidente.backend.users.repository.RoleRepository;
import com.cafeoccidente.backend.users.repository.UserRepository;
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
 * HU-30 (RF-21.2) y HU-29 CA-4 sobre la semilla (agencia 1), con el servicio real de compras. Transaccional: no deja
 * nada. Cada compra pesa 1000 kg brutos sin destare, asi que sus kilos netos son 1000.
 */
@SpringBootTest
@Transactional
@SuppressWarnings("null")
class QuotaPurchaseIntegrationTest {

    private static final String SELLER = "990003030";
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
    /** El anuncio con cupo (mas viejo) y el vigente del mismo especial, con otro precio. */
    private Integer quotaAnnouncement;
    private Integer latestAnnouncement;

    @BeforeEach
    void quotaAnnouncementAndANewerOne() {
        User user = new User();
        user.setUsername("prueba_4b");
        user.setPasswordHash("x");
        user.setRole(roles.findByName(Roles.ADMINISTRADOR).orElseThrow());
        user.setAgency(agencies.findById(AGENCY).orElseThrow());
        SecurityUser principal = new SecurityUser(users.save(user));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        rp = jdbc.queryForObject("SELECT id FROM fondos WHERE codigo = 'RP'", Long.class);
        Map<String, Object> withQuota = announcement(1546900);
        quotaAnnouncement = ((Number) withQuota.get("numero_anuncio")).intValue();
        jdbc.update("INSERT INTO cupos_anuncios (anuncio_agencia_id, cupo) VALUES (?, 2500)", withQuota.get("id"));
        latestAnnouncement = ((Number) announcement(1800000).get("numero_anuncio")).intValue();
        jdbc.update("INSERT INTO terceros (identificacion, primer_nombre, primer_apellido, agencia_id, direccion, telefono, tipo,"
                + " asociacion) VALUES (?, 'PRUEBA', 'CUPOS', ?, 'VEREDA', '3001112233', 'C', false)", SELLER, AGENCY);
    }

    private Map<String, Object> announcement(int basePrice) {
        Long id = jdbc.queryForObject("INSERT INTO anuncios (fecha_anuncio, pr_base_cps, pr_alm_defec, sobrepr_carga_esp,"
                + " pr_inc_calidad, fondo_id, especial, activo) VALUES (CURRENT_DATE, ?, 6000, 80000, 50, ?, 'RN', true)"
                + " RETURNING id", Long.class, basePrice, rp);
        return jdbc.queryForMap("INSERT INTO anuncios_agencias (agencia_id, anuncio_id, numero_anuncio, fecha_asignacion)"
                + " SELECT ?, ?, COALESCE(MAX(numero_anuncio), 0) + 1, CURRENT_DATE FROM anuncios_agencias"
                + " WHERE agencia_id = ? RETURNING id, numero_anuncio", AGENCY, id, AGENCY);
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    private DryCoffeePurchaseRequest request(Integer announcement, BigDecimal delivery, PurchasePaymentRequest payment) {
        return new DryCoffeePurchaseRequest(rp, "RN", SELLER, null, null, 5, KG, BigDecimal.ZERO, new BigDecimal("200"),
                new BigDecimal("10"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, payment, announcement, delivery, null);
    }

    private DryCoffeePurchaseResponse buy(Integer announcement, BigDecimal delivery) {
        BigDecimal net = dry.preview(request(announcement, delivery, null)).netToPay();
        return dry.create(request(announcement, delivery,
                new PurchasePaymentRequest(net, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null)));
    }

    private BigDecimal balance() {
        return quotas.get(AGENCY, quotaAnnouncement).balance();
    }

    @Test
    void theWindowListsTheQuotaAnnouncementsOfTheFundAndSpecialWithBalance() {
        List<QuotaBalanceResponse> rows = quotas.withBalance(AGENCY, rp, "RN");

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.announcement().announcementNumber()).endsWith("-" + quotaAnnouncement);
            assertThat(row.assignedQuota()).isEqualByComparingTo("2500");
            assertThat(row.deliveredKg()).isZero();
            assertThat(row.balance()).isEqualByComparingTo("2500");
        });
        assertThat(quotas.withBalance(AGENCY, rp, "ESTANDAR")).isEmpty();
    }

    @Test
    void ca4BillsWithThePricesOfTheChosenAnnouncementAndTheBalanceGoesDown() {
        String chosenPrice = dry.specialInfo(AGENCY, rp, "RN", quotaAnnouncement, null).healthyUnitPrice().toPlainString();
        assertThat(dry.specialInfo(AGENCY, rp, "RN", null, null).healthyUnitPrice()).isNotEqualByComparingTo(chosenPrice);

        DryCoffeePurchaseResponse p = buy(quotaAnnouncement, KG);

        Map<String, Object> row = jdbc.queryForMap("SELECT numero_anuncio, pr_alm_sana FROM compras_cafe_seco WHERE id = ?", p.id());
        assertThat((String) row.get("numero_anuncio")).endsWith("-" + quotaAnnouncement);
        assertThat((BigDecimal) row.get("pr_alm_sana")).isEqualByComparingTo(chosenPrice);
        assertThat(balance()).isEqualByComparingTo("1500");
    }

    @Test
    void ca3DeliveryAboveTheBalanceIsRejectedAndNothingIsSaved() {
        Long before = jdbc.queryForObject("SELECT count(*) FROM compras_cafe_seco", Long.class);

        assertThatThrownBy(() -> buy(quotaAnnouncement, new BigDecimal("2500.01")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("supera el saldo");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM compras_cafe_seco", Long.class)).isEqualTo(before);
        assertThat(balance()).isEqualByComparingTo("2500");
    }

    @Test
    void netKgAboveTheDeliveryIsRejected() {
        assertThatThrownBy(() -> buy(quotaAnnouncement, new BigDecimal("999")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("superan Esta Entrega");
    }

    @Test
    void thePreviewAlreadyWarnsWhenTheNetKgPassTheDelivery() {
        DryCoffeePurchaseRequest request = request(quotaAnnouncement, new BigDecimal("999"), null);

        assertThatThrownBy(() -> dry.preview(request))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("superan Esta Entrega");
    }

    @Test
    void theChosenAnnouncementNeedsQuotaAndADelivery() {
        assertThatThrownBy(() -> buy(latestAnnouncement, KG))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("no tiene cupo");
        assertThatThrownBy(() -> dry.preview(request(quotaAnnouncement, null, null)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Esta Entrega");
    }

    @Test
    void anAnnouncementOfAnotherSpecialIsRejected() {
        DryCoffeePurchaseRequest otherSpecial = new DryCoffeePurchaseRequest(rp, "ESTANDAR", SELLER, null, null, 5, KG,
                BigDecimal.ZERO, new BigDecimal("200"), new BigDecimal("10"), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, null, quotaAnnouncement, KG, null);

        assertThatThrownBy(() -> dry.preview(otherSpecial))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("no es de ese fondo y especial");
    }

    @Test
    void hu29Ca4WithBalanceUsedUpNobodyBuysWithThatAnnouncement() {
        buy(quotaAnnouncement, KG);
        buy(quotaAnnouncement, KG);
        assertThat(balance()).isEqualByComparingTo("500");

        assertThatThrownBy(() -> buy(quotaAnnouncement, KG))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("supera el saldo");
        // Tampoco desde Compras Cafe Seco cuando el anuncio con cupo es el vigente.
        jdbc.update("UPDATE anuncios SET activo = false WHERE id = (SELECT anuncio_id FROM anuncios_agencias"
                + " WHERE agencia_id = ? AND numero_anuncio = ?)", AGENCY, latestAnnouncement);
        assertThatThrownBy(() -> buy(null, null))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("superan el saldo del cupo");
        assertThat(quotas.withBalance(AGENCY, rp, "RN")).extracting(QuotaBalanceResponse::balance)
                .singleElement().satisfies(b -> assertThat(b).isEqualByComparingTo("500"));
    }

    @Test
    void annullingThePurchaseGivesTheKilosBackToTheBalance() {
        DryCoffeePurchaseResponse p = buy(quotaAnnouncement, KG);
        assertThat(balance()).isEqualByComparingTo("1500");

        annulment.annul(AGENCY, AnnulmentModule.DRY, p.id());

        assertThat(balance()).isEqualByComparingTo("2500");
    }

    @Test
    void aPurchaseWithAnAnnouncementWithoutQuotaIsNotLimited() {
        assertThat(buy(null, null).netKg()).isEqualByComparingTo(KG);
    }
}
