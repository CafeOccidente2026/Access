package com.cafeoccidente.backend.purchases.query.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cafeoccidente.backend.common.exception.ResourceNotFoundException;
import com.cafeoccidente.backend.common.security.Roles;
import com.cafeoccidente.backend.common.security.SecurityUser;
import com.cafeoccidente.backend.purchases.annulment.repository.AnnulmentDirection;
import com.cafeoccidente.backend.purchases.annulment.repository.AnnulmentModule;
import com.cafeoccidente.backend.purchases.annulment.service.PurchaseAnnulmentService;
import com.cafeoccidente.backend.purchases.drycoffee.dto.DryCoffeePurchaseRequest;
import com.cafeoccidente.backend.purchases.drycoffee.dto.DryCoffeePurchaseResponse;
import com.cafeoccidente.backend.purchases.drycoffee.service.DryCoffeePurchaseService;
import com.cafeoccidente.backend.purchases.greencoffee.dto.GreenCoffeePurchaseRequest;
import com.cafeoccidente.backend.purchases.greencoffee.dto.GreenCoffeePurchaseResponse;
import com.cafeoccidente.backend.purchases.greencoffee.service.GreenCoffeePurchaseService;
import com.cafeoccidente.backend.purchases.husk.dto.HuskPurchaseRequest;
import com.cafeoccidente.backend.purchases.husk.dto.HuskPurchaseResponse;
import com.cafeoccidente.backend.purchases.husk.service.HuskPurchaseService;
import com.cafeoccidente.backend.purchases.query.dto.PurchaseQueryDetail;
import com.cafeoccidente.backend.purchases.query.service.PurchaseQueryService;
import com.cafeoccidente.backend.purchases.shared.dto.PurchasePaymentRequest;
import com.cafeoccidente.backend.purchases.shared.repository.AgencyRepository;
import com.cafeoccidente.backend.users.entity.User;
import com.cafeoccidente.backend.users.repository.RoleRepository;
import com.cafeoccidente.backend.users.repository.UserRepository;
import java.math.BigDecimal;
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
 * 6g3 (HU-28, RF-26): Consultar Compras de la agencia de trabajo. El detalle trae la compra con todos
 * sus campos, el ESTADO, las formas de pago guardadas y la remision. Sobre la semilla (agencia 1), asi
 * corre en CI. Transaccional: no deja nada.
 */
@SpringBootTest
@Transactional
@SuppressWarnings("null")
class PurchaseQueryIntegrationTest {

    private static final String SELLER = "990000999";
    private static final long AGENCY = 1L;
    private static final BigDecimal CHECK = new BigDecimal("300000");
    private static final BigDecimal TRANSFER = new BigDecimal("200000");
    private static final BigDecimal CARD = new BigDecimal("100000");

    @Autowired
    private PurchaseQueryService query;
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
    private Long userId;

    @BeforeEach
    void pointWithAnnouncementsAndASeller() {
        User user = new User();
        user.setUsername("prueba_6g3");
        user.setPasswordHash("x");
        user.setRole(roles.findByName(Roles.USUARIO).orElseThrow());
        user.setAgency(agencies.findById(AGENCY).orElseThrow());
        SecurityUser principal = new SecurityUser(users.save(user));
        userId = principal.getUserId();
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
                + " asociacion) VALUES (?, 'PRUEBA', 'CONSULTA', ?, 'VEREDA', '3001112233', 'C', false)", SELLER, AGENCY);
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

    private DryCoffeePurchaseResponse mixedDry() {
        BigDecimal net = dry.preview(dryRequest(null)).netToPay();
        return dry.create(dryRequest(new PurchasePaymentRequest(
                net.subtract(CHECK).subtract(TRANSFER).subtract(CARD), CHECK, TRANSFER, CARD, "4321")));
    }

    @Test
    void theDetailHasThePurchaseItsStatusAndItsSavedPaymentMethods() {
        DryCoffeePurchaseResponse saved = mixedDry();

        PurchaseQueryDetail detail = query.detail(AnnulmentModule.DRY, saved.id());

        assertThat(detail.module()).isEqualTo(AnnulmentModule.DRY);
        assertThat(detail.status()).isEqualTo("VALIDA");
        assertThat(detail.purchase()).isInstanceOfSatisfying(DryCoffeePurchaseResponse.class,
                p -> assertThat(p.invoiceNumber()).isEqualTo(saved.invoiceNumber()));
        assertThat(detail.payment().checkAmount()).isEqualByComparingTo(CHECK);
        assertThat(detail.payment().transferAmount()).isEqualByComparingTo(TRANSFER);
        assertThat(detail.payment().cardAmount()).isEqualByComparingTo(CARD);
        assertThat(detail.payment().cashAmount().add(CHECK).add(TRANSFER).add(CARD)).isEqualByComparingTo(saved.netToPay());
        assertThat(detail.payment().checkNumber()).isEqualTo("4321");
        assertThat(detail.remissions()).isEmpty();
    }

    @Test
    void rn265AnAnnulledPurchaseSaysAnulada() {
        DryCoffeePurchaseResponse saved = mixedDry();
        annulment.annul(AGENCY, AnnulmentModule.DRY, saved.id());

        assertThat(query.detail(AnnulmentModule.DRY, saved.id()).status()).isEqualTo("ANULADA");
    }

    @Test
    void greenAndHuskBringTheirOwnPurchase() {
        GreenCoffeePurchaseRequest greenPreview = new GreenCoffeePurchaseRequest(rp, SELLER, null, null, 5,
                new BigDecimal("1000"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("12000"), BigDecimal.ZERO,
                BigDecimal.ZERO, null);
        var g = green.create(new GreenCoffeePurchaseRequest(rp, SELLER, null, null, 5, new BigDecimal("1000"),
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("12000"), BigDecimal.ZERO, BigDecimal.ZERO,
                cash(green.preview(greenPreview).netToPay())));
        HuskPurchaseRequest huskPreview = new HuskPurchaseRequest(rp, SELLER, null, null, new BigDecimal("200"), 5,
                new BigDecimal("1000"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null);
        var h = husk.create(new HuskPurchaseRequest(rp, SELLER, null, null, new BigDecimal("200"), 5,
                new BigDecimal("1000"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, cash(husk.preview(huskPreview).netToPay())));

        assertThat(query.detail(AnnulmentModule.GREEN, g.id()).purchase()).isInstanceOf(GreenCoffeePurchaseResponse.class);
        assertThat(query.detail(AnnulmentModule.HUSK, h.id()).purchase()).isInstanceOf(HuskPurchaseResponse.class);
    }

    @Test
    void showsTheRemissionWhereItsInventoryLeft() {
        DryCoffeePurchaseResponse saved = mixedDry();
        Long inventory = jdbc.queryForObject("SELECT id FROM inventarios WHERE modulo_compra = 'DRY_COFFEE' AND compra_id = ?",
                Long.class, saved.id());
        Long remission = jdbc.queryForObject("INSERT INTO remisiones (numero_remision_anterior, agencia_id, fecha_remision,"
                + " creado_por_usuario_id, creado_en, numero_remision) VALUES (0, ?, CURRENT_DATE, ?, now(), 'SDBU-RP-9999')"
                + " RETURNING id", Long.class, AGENCY, userId);
        jdbc.update("INSERT INTO remisiones_detalle (remision_id, inventario_id, cantidad, valor_unitario, valor_salida)"
                + " VALUES (?, ?, 100, 1, 100)", remission, inventory);

        assertThat(query.detail(AnnulmentModule.DRY, saved.id()).remissions()).containsExactly("SDBU-RP-9999");
    }

    @Test
    void rn263APurchaseOfAnotherAgencyIsNotShown() {
        DryCoffeePurchaseResponse saved = mixedDry();
        jdbc.update("UPDATE compras_cafe_seco SET agencia_id = (SELECT min(id) FROM agencias WHERE id <> ?) WHERE id = ?",
                AGENCY, saved.id());

        assertThatThrownBy(() -> query.detail(AnnulmentModule.DRY, saved.id())).isInstanceOf(ResourceNotFoundException.class);
        assertThat(query.findByInvoice(saved.invoiceNumber())).isEmpty();
    }

    @Test
    void ca1AndCa2WalkAndFindTheInvoicesOfTheWorkingAgency() {
        DryCoffeePurchaseResponse first = mixedDry();
        DryCoffeePurchaseResponse second = mixedDry();

        assertThat(query.findByInvoice(first.invoiceNumber())).singleElement()
                .satisfies(c -> assertThat(c.id()).isEqualTo(first.id()));
        assertThat(query.navigate(first.invoiceNumber(), AnnulmentDirection.NEXT)).singleElement()
                .satisfies(c -> assertThat(c.id()).isEqualTo(second.id()));
        assertThat(query.navigate(null, AnnulmentDirection.LAST)).singleElement()
                .satisfies(c -> assertThat(c.id()).isEqualTo(second.id()));
    }
}
