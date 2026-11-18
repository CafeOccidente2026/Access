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
import com.cafeoccidente.backend.purchases.future.dto.FuturePurchaseResponse;
import com.cafeoccidente.backend.purchases.future.service.FuturePurchaseService;
import com.cafeoccidente.backend.purchases.shared.dto.PurchasePaymentRequest;
import com.cafeoccidente.backend.purchases.shared.repository.AgencyRepository;
import com.cafeoccidente.backend.users.entity.User;
import com.cafeoccidente.backend.users.repository.RoleRepository;
import com.cafeoccidente.backend.users.repository.UserRepository;
import java.math.BigDecimal;
import java.util.function.Function;
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
 * HU-33 (RF-21.5) sobre la semilla (agencia 1), con el servicio real de compras. Transaccional: no deja nada.
 * El compromiso es de 2500 kg de RN con precios propios (Pr Alm Sana 9100); cada compra pesa 1000 kg netos.
 */
@SpringBootTest
@Transactional
@SuppressWarnings("null")
class FutureBillingIntegrationTest {

    private static final String SELLER = "990003300";
    private static final String OTHER_SELLER = "990003301";
    private static final long AGENCY = 1L;
    private static final BigDecimal KG = new BigDecimal("1000");

    @Autowired
    private DryCoffeePurchaseService dry;
    @Autowired
    private FuturePurchaseService futures;
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

    @BeforeEach
    void aFuturePurchaseWithItsOwnPrices() {
        User user = new User();
        user.setUsername("prueba_4d_anunciadas");
        user.setPasswordHash("x");
        user.setRole(roles.findByName(Roles.ADMINISTRADOR).orElseThrow());
        user.setAgency(agencies.findById(AGENCY).orElseThrow());
        SecurityUser principal = new SecurityUser(users.save(user));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        rp = jdbc.queryForObject("SELECT id FROM fondos WHERE codigo = 'RP'", Long.class);
        // Anuncio vigente de RN con otro precio: la compra no lo debe usar.
        Long announcement = jdbc.queryForObject("INSERT INTO anuncios (fecha_anuncio, pr_base_cps, pr_alm_defec,"
                + " sobrepr_carga_esp, pr_inc_calidad, fondo_id, especial, activo) VALUES (CURRENT_DATE, 1800000, 6000, 80000,"
                + " 50, ?, 'RN', true) RETURNING id", Long.class, rp);
        jdbc.update("INSERT INTO anuncios_agencias (agencia_id, anuncio_id, numero_anuncio, fecha_asignacion)"
                + " SELECT ?, ?, COALESCE(MAX(numero_anuncio), 0) + 1, CURRENT_DATE FROM anuncios_agencias WHERE agencia_id = ?",
                AGENCY, announcement, AGENCY);
        for (String seller : new String[] {SELLER, OTHER_SELLER}) {
            jdbc.update("INSERT INTO terceros (identificacion, primer_nombre, primer_apellido, agencia_id, direccion, telefono,"
                    + " tipo, asociacion) VALUES (?, 'PRUEBA', 'ANUNCIADA', ?, 'VEREDA', '3001112233', 'C', false)", seller, AGENCY);
        }
        future = insertFuture(SELLER, "2500");
    }

    private Long insertFuture(String seller, String kg) {
        return jdbc.queryForObject("INSERT INTO compras_anunciadas (creado_en, agencia_id, numero_anuncio, fecha_anuncio,"
                + " cedula, nombres, tipo, direccion, especial, kilos, saldo, fecha_entrega, pr_alm_sana, pr_alm_defec,"
                + " bonificacion, costos, pr_inc_calidad, pr_base_cps, creado_por_usuario_id) VALUES (now(), ?, 'SDBU-777',"
                + " CURRENT_DATE - 3, ?, 'PRUEBA', 'C', 'VEREDA', 'RN', ?, ?, CURRENT_DATE + 30, 9100, 6000, 30, 692, 0,"
                + " 1500000, 1) RETURNING id", Long.class, AGENCY, seller, new BigDecimal(kg), new BigDecimal(kg));
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    private DryCoffeePurchaseRequest request(String seller, String special, Long futureId, BigDecimal delivery,
            PurchasePaymentRequest payment) {
        return new DryCoffeePurchaseRequest(rp, special, seller, null, null, 5, KG, BigDecimal.ZERO, new BigDecimal("200"),
                new BigDecimal("10"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, payment, null, delivery, futureId);
    }

    private DryCoffeePurchaseResponse bill(String seller, String special, Long futureId, BigDecimal delivery) {
        BigDecimal net = dry.preview(request(seller, special, futureId, delivery, null)).netToPay();
        return dry.create(request(seller, special, futureId, delivery,
                new PurchasePaymentRequest(net, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null)));
    }

    private BigDecimal balance() {
        return jdbc.queryForObject("SELECT saldo FROM compras_anunciadas WHERE id = ?", BigDecimal.class, future);
    }

    @Test
    void ca1TheWindowListsTheOpenFuturePurchasesOfThatIdNumber() {
        Long empty = insertFuture(SELLER, "0");

        assertThat(futures.openFor(SELLER)).extracting(FuturePurchaseResponse::id).containsExactly(future).doesNotContain(empty);
        assertThat(futures.openFor(OTHER_SELLER)).isEmpty();
    }

    @Test
    void ca2BillsWithThePricesAndSpecialOfTheFuturePurchaseAndTheBalanceGoesDown() {
        DryCoffeePurchaseResponse p = bill(SELLER, "RN", future, KG);

        Map<String, Object> row = jdbc.queryForMap("SELECT numero_anuncio, especial, pr_alm_sana, bonificacion,"
                + " compra_anunciada_id FROM compras_cafe_seco WHERE id = ?", p.id());
        assertThat(row.get("numero_anuncio")).isEqualTo("SDBU-777");
        assertThat(row.get("especial")).isEqualTo("RN");
        assertThat((BigDecimal) row.get("pr_alm_sana")).isEqualByComparingTo("9100");
        assertThat((BigDecimal) row.get("bonificacion")).isEqualByComparingTo("30");
        assertThat(((Number) row.get("compra_anunciada_id")).longValue()).isEqualTo(future);
        assertThat(balance()).isEqualByComparingTo("1500");
    }

    @Test
    void theIdNumberMustBeTheOneOfTheFuturePurchase() {
        assertThatThrownBy(() -> bill(OTHER_SELLER, "RN", future, KG))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("cédula");
        assertThat(balance()).isEqualByComparingTo("2500");
    }

    @Test
    void theSpecialMustBeTheOneOfTheFuturePurchase() {
        assertThatThrownBy(() -> bill(SELLER, "ESTANDAR", future, KG))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("especial");
    }

    @Test
    void ca2DeliveryAboveTheBalanceIsRejected() {
        assertThatThrownBy(() -> bill(SELLER, "RN", future, new BigDecimal("2500.01")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("supera el saldo");
        assertThat(balance()).isEqualByComparingTo("2500");
    }

    /**
     * Prueba del 11-nov (punto 9): Kilos Netos no se digita. Con Esta Entrega y Kilos Brutos el servidor pone el
     * Destare (lo que sobra) y los Netos (la entrega); si los brutos no alcanzan, es una entrega parcial: Netos =
     * Brutos y Destare 0, y el saldo baja solo en lo entregado. Lo que mande el cliente en Destare se ignora.
     */
    @Test
    void estaEntregaDecidesTheTareAndTheNetKg() {
        DryCoffeePurchaseResponse over = billGross("305", "300", "50");
        assertThat(over.tareKg()).isEqualByComparingTo("5");
        assertThat(over.netKg()).isEqualByComparingTo("300");
        assertThat(balance()).isEqualByComparingTo("2200");

        DryCoffeePurchaseResponse exact = billGross("300", "300", "0");
        assertThat(exact.tareKg()).isZero();
        assertThat(exact.netKg()).isEqualByComparingTo("300");
        assertThat(balance()).isEqualByComparingTo("1900");

        DryCoffeePurchaseResponse partial = billGross("250", "300", "0");
        assertThat(partial.tareKg()).isZero();
        assertThat(partial.netKg()).isEqualByComparingTo("250");
        assertThat(balance()).isEqualByComparingTo("1650");
    }

    private DryCoffeePurchaseResponse billGross(String gross, String delivery, String typedTare) {
        Function<PurchasePaymentRequest, DryCoffeePurchaseRequest> request = payment -> new DryCoffeePurchaseRequest(rp, "RN",
                SELLER, null, null, 5, new BigDecimal(gross), new BigDecimal(typedTare), new BigDecimal("200"),
                new BigDecimal("10"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, payment, null,
                new BigDecimal(delivery), future);
        BigDecimal net = dry.preview(request.apply(null)).netToPay();
        return dry.create(request.apply(new PurchasePaymentRequest(net, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null)));
    }

    @Test
    void ca3WithTheBalanceInZeroItLeavesTheWindowAndCanNotBeBilled() {
        bill(SELLER, "RN", future, KG);
        bill(SELLER, "RN", future, KG);
        jdbc.update("UPDATE compras_anunciadas SET saldo = 0 WHERE id = ?", future);

        assertThat(futures.openFor(SELLER)).isEmpty();
        assertThatThrownBy(() -> bill(SELLER, "RN", future, KG))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("supera el saldo");
    }

    @Test
    void annullingThePurchaseGivesTheKilosBackToTheFuturePurchase() {
        DryCoffeePurchaseResponse p = bill(SELLER, "RN", future, KG);
        assertThat(balance()).isEqualByComparingTo("1500");

        annulment.annul(AGENCY, AnnulmentModule.DRY, p.id());

        assertThat(balance()).isEqualByComparingTo("2500");
    }

    @Test
    void thePreviewAlreadyWarnsWhenTheDeliveryPassesTheBalance() {
        jdbc.update("UPDATE compras_anunciadas SET saldo = 500 WHERE id = ?", future);
        DryCoffeePurchaseRequest request = request(SELLER, "RN", future, new BigDecimal("600"), null);

        assertThatThrownBy(() -> dry.preview(request))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("supera el saldo de la compra anunciada");
    }

    @Test
    void aFuturePurchaseNeedsEstaEntrega() {
        assertThatThrownBy(() -> dry.preview(request(SELLER, "RN", future, null, null)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Esta Entrega");
    }

    @Test
    void theSpecialInfoShowsThePricesOfTheFuturePurchase() {
        assertThat(dry.specialInfo(AGENCY, rp, "RN", null, future).healthyUnitPrice()).isEqualByComparingTo("9100");
    }
}
