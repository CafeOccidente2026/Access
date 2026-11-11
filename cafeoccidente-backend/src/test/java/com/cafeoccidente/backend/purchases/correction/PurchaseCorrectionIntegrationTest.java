package com.cafeoccidente.backend.purchases.correction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.common.security.Roles;
import com.cafeoccidente.backend.common.security.SecurityUser;
import com.cafeoccidente.backend.purchases.annulment.repository.AnnulmentModule;
import com.cafeoccidente.backend.purchases.annulment.service.PurchaseAnnulmentService;
import com.cafeoccidente.backend.purchases.drycoffee.dto.DryCoffeePurchaseRequest;
import com.cafeoccidente.backend.purchases.drycoffee.dto.DryCoffeePurchaseResponse;
import com.cafeoccidente.backend.purchases.drycoffee.service.DryCoffeePurchaseService;
import com.cafeoccidente.backend.purchases.future.service.AnnouncementQuotaService;
import com.cafeoccidente.backend.purchases.greencoffee.dto.GreenCoffeePurchaseRequest;
import com.cafeoccidente.backend.purchases.greencoffee.service.GreenCoffeePurchaseService;
import com.cafeoccidente.backend.purchases.husk.dto.HuskPurchaseRequest;
import com.cafeoccidente.backend.purchases.husk.service.HuskPurchaseService;
import com.cafeoccidente.backend.purchases.query.service.PurchaseQueryService;
import com.cafeoccidente.backend.purchases.shared.dto.PurchasePaymentRequest;
import com.cafeoccidente.backend.purchases.shared.repository.AgencyRepository;
import com.cafeoccidente.backend.users.entity.User;
import com.cafeoccidente.backend.users.repository.RoleRepository;
import com.cafeoccidente.backend.users.repository.UserRepository;
import jakarta.persistence.EntityManager;
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
 * 6D1 (HU-05, respuesta del area 11-nov): corregir una compra sin anularla. Conserva la factura, recalcula
 * con los precios del anuncio original y deja cuadrados pagos, cuenta por cobrar, inventario, cupo y compra
 * anunciada. Sobre la semilla (agencia 1), asi corre tambien en CI. Transaccional: no deja nada.
 */
@SpringBootTest
@Transactional
@SuppressWarnings("null")
class PurchaseCorrectionIntegrationTest {

    private static final String SELLER = "990000888";
    private static final long AGENCY = 1L;
    private static final BigDecimal CARD = new BigDecimal("100000");
    private static final BigDecimal QUOTA = new BigDecimal("5000");

    @Autowired
    private DryCoffeePurchaseService dry;
    @Autowired
    private GreenCoffeePurchaseService green;
    @Autowired
    private HuskPurchaseService husk;
    @Autowired
    private PurchaseAnnulmentService annulment;
    @Autowired
    private AnnouncementQuotaService quotas;
    @Autowired
    private PurchaseQueryService query;
    @Autowired
    private UserRepository users;
    @Autowired
    private RoleRepository roles;
    @Autowired
    private AgencyRepository agencies;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EntityManager entityManager;

    private Long rp;
    private Long userId;
    private Integer announcementNumber;

    @BeforeEach
    void pointWithAnnouncementsAQuotaAndASeller() {
        User user = new User();
        user.setUsername("prueba_6d1");
        user.setPasswordHash("x");
        user.setRole(roles.findByName(Roles.ADMINISTRADOR).orElseThrow());
        user.setAgency(agencies.findById(AGENCY).orElseThrow());
        SecurityUser principal = new SecurityUser(users.save(user));
        userId = principal.getUserId();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        rp = jdbc.queryForObject("SELECT id FROM fondos WHERE codigo = 'RP'", Long.class);
        for (String special : new String[] {"CV", "PASILLA", "RN"}) {
            Map<String, Object> numbering = announce(special, 1546900);
            if (special.equals("RN")) {
                announcementNumber = ((Number) numbering.get("numero_anuncio")).intValue();
                jdbc.update("INSERT INTO cupos_anuncios (anuncio_agencia_id, cupo) VALUES (?, ?)", numbering.get("id"), QUOTA);
            }
        }
        jdbc.update("UPDATE registros_control SET peso_tot_alm_cv = 140, peso_tot_pasilla_cv = 12.5, peso_alm_sana_cv = 186.18"
                + " WHERE agencia_id = ? AND activo", AGENCY);
        jdbc.update("INSERT INTO terceros (identificacion, primer_nombre, primer_apellido, agencia_id, direccion, telefono, tipo,"
                + " asociacion) VALUES (?, 'PRUEBA', 'CORRECCION', ?, 'VEREDA', '3001112233', 'C', false)", SELLER, AGENCY);
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    /** Anuncio nuevo de ese especial en la agencia: queda como el vigente. */
    private Map<String, Object> announce(String special, long basePrice) {
        Long id = jdbc.queryForObject("INSERT INTO anuncios (fecha_anuncio, pr_base_cps, pr_alm_defec, sobrepr_carga_esp,"
                + " pr_inc_calidad, fondo_id, especial, activo) VALUES (CURRENT_DATE, ?, 6000, 80000, 50, ?, ?, true)"
                + " RETURNING id", Long.class, basePrice, rp, special);
        return jdbc.queryForMap("INSERT INTO anuncios_agencias (agencia_id, anuncio_id, numero_anuncio, fecha_asignacion)"
                + " SELECT ?, ?, COALESCE(MAX(numero_anuncio), 0) + 1, CURRENT_DATE FROM anuncios_agencias"
                + " WHERE agencia_id = ? RETURNING id, numero_anuncio", AGENCY, id, AGENCY);
    }

    private static PurchasePaymentRequest cashAndCard(BigDecimal net, BigDecimal card) {
        return new PurchasePaymentRequest(net.subtract(card), BigDecimal.ZERO, BigDecimal.ZERO, card, null);
    }

    private DryCoffeePurchaseRequest dryRequest(String grossKg, PurchasePaymentRequest payment) {
        return new DryCoffeePurchaseRequest(rp, "RN", SELLER, null, null, 5, new BigDecimal(grossKg), BigDecimal.ZERO,
                new BigDecimal("200"), new BigDecimal("10"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, payment);
    }

    private BigDecimal dryNet(String grossKg) {
        return dry.preview(dryRequest(grossKg, null)).netToPay();
    }

    private DryCoffeePurchaseResponse dryPurchase() {
        return dry.create(dryRequest("1000", cashAndCard(dryNet("1000"), CARD)));
    }

    /** Corrige los kilos brutos con el mismo pago en efectivo y datafono. El neto lo calcula el servidor. */
    private DryCoffeePurchaseResponse correctDry(DryCoffeePurchaseResponse p, String grossKg, BigDecimal card) {
        BigDecimal net = dry.previewCorrection(p.id(), dryRequest(grossKg, null)).netToPay();
        return dry.correct(p.id(), dryRequest(grossKg, cashAndCard(net, card)));
    }

    private List<Map<String, Object>> receivables(String module, Long id) {
        return jdbc.queryForList("SELECT estado, valor FROM cuentas_por_cobrar WHERE modulo_compra = ? AND compra_id = ?",
                module, id);
    }

    private Map<String, Object> inventory(String module, Long id) {
        return jdbc.queryForMap("SELECT kilos_netos, saldo_kilos, vr_inventario, sacos FROM inventarios"
                + " WHERE modulo_compra = ? AND compra_id = ?", module, id);
    }

    @Test
    void correctsKilosAndPaymentKeepingTheSameInvoice() {
        DryCoffeePurchaseResponse p = dryPurchase();
        BigDecimal card = new BigDecimal("50000");
        assertThat(query.detail(AnnulmentModule.DRY, p.id()).corrected()).isFalse();

        DryCoffeePurchaseResponse fixed = correctDry(p, "800", card);

        assertThat(fixed.invoiceNumber()).isEqualTo(p.invoiceNumber());
        assertThat(fixed.netKg()).isLessThan(p.netKg());
        assertThat(fixed.netToPay()).isLessThan(p.netToPay());
        assertThat(fixed.paymentMethod()).isEqualTo("MIXTO");
        // Lo pagado cuadra con el neto nuevo: Caja y Suministros se arman de formas_pago_compras.
        Map<String, Object> split = jdbc.queryForMap("SELECT efectivo + cheque + transferencia + datafono AS total, datafono"
                + " FROM formas_pago_compras WHERE origen = 'DRY' AND origen_id = ?", p.id());
        assertThat((BigDecimal) split.get("total")).isEqualByComparingTo(fixed.netToPay());
        assertThat((BigDecimal) split.get("datafono")).isEqualByComparingTo(card);
        assertThat(receivables("DRY", p.id())).singleElement().satisfies(r -> {
            assertThat(r).containsEntry("estado", "PENDIENTE");
            assertThat((BigDecimal) r.get("valor")).isEqualByComparingTo(card);
        });
        // El inventario y el cupo del anuncio quedan con los kilos nuevos.
        Map<String, Object> stock = inventory("DRY_COFFEE", p.id());
        assertThat((BigDecimal) stock.get("kilos_netos")).isEqualByComparingTo(fixed.netKg());
        assertThat((BigDecimal) stock.get("saldo_kilos")).isEqualByComparingTo(fixed.netKg());
        assertThat((BigDecimal) stock.get("vr_inventario")).isEqualByComparingTo(fixed.inventoryValue());
        assertThat(quotas.get(AGENCY, announcementNumber).balance()).isEqualByComparingTo(QUOTA.subtract(fixed.netKg()));
        // Marcada como corregida, por quien y cuando, y en el Historial con el antes y el despues.
        Map<String, Object> mark = jdbc.queryForMap("SELECT corregida, corregida_en, corregida_por_usuario_id"
                + " FROM compras_cafe_seco WHERE id = ?", p.id());
        assertThat(mark).containsEntry("corregida", true).containsEntry("corregida_por_usuario_id", userId);
        assertThat(mark.get("corregida_en")).isNotNull();
        // 6D2: Consultar Compras la muestra como corregida.
        assertThat(query.detail(AnnulmentModule.DRY, p.id()).corrected()).isTrue();
        Map<String, Object> history = jdbc.queryForMap("SELECT accion, agencia_id, antes::text AS antes, despues::text AS despues"
                + " FROM historial_cambios WHERE entidad = 'COMPRA' AND entidad_id = ?", p.invoiceNumber().longValue());
        assertThat(history).containsEntry("accion", "CORREGIR").containsEntry("agencia_id", AGENCY);
        assertThat((String) history.get("antes")).contains(p.netKg().stripTrailingZeros().toPlainString());
        assertThat((String) history.get("despues")).contains(fixed.netKg().stripTrailingZeros().toPlainString());
    }

    @Test
    void recalculatesWithTheOriginalAnnouncementEvenIfThereIsANewerOne() {
        DryCoffeePurchaseResponse p = dryPurchase();
        announce("RN", 2500000);
        // La vista previa de la correccion usa el anuncio original; la de una compra nueva, el vigente (mas caro).
        assertThat(dry.previewCorrection(p.id(), dryRequest("1000", null)).netToPay()).isEqualByComparingTo(p.netToPay());
        assertThat(dryNet("1000")).isGreaterThan(p.netToPay());

        DryCoffeePurchaseResponse fixed = dry.correct(p.id(), dryRequest("1000", cashAndCard(p.netToPay(), CARD)));

        assertThat(fixed.announcementNumber()).isEqualTo(p.announcementNumber());
        assertThat(fixed.healthyUnitPrice()).isEqualByComparingTo(p.healthyUnitPrice());
        assertThat(fixed.unitPrice()).isEqualByComparingTo(p.unitPrice());
        // La retencion se calcula sin sumar la misma compra a lo vendido en el dia.
        assertThat(fixed.withholding()).isEqualByComparingTo(p.withholding());
        assertThat(fixed.netToPay()).isEqualByComparingTo(p.netToPay());
    }

    @Test
    void removingTheCardRemovesTheReceivable() {
        DryCoffeePurchaseResponse p = dryPurchase();

        correctDry(p, "1000", BigDecimal.ZERO);

        assertThat(receivables("DRY", p.id())).isEmpty();
    }

    @Test
    void addingACardCreatesTheReceivable() {
        DryCoffeePurchaseResponse p = dry.create(dryRequest("1000", cashAndCard(dryNet("1000"), BigDecimal.ZERO)));

        correctDry(p, "1000", CARD);

        assertThat(receivables("DRY", p.id())).singleElement()
                .satisfies(r -> assertThat((BigDecimal) r.get("valor")).isEqualByComparingTo(CARD));
    }

    @Test
    void cannotCorrectAnAnnulledPurchase() {
        DryCoffeePurchaseResponse p = dryPurchase();
        annulment.annul(AGENCY, AnnulmentModule.DRY, p.id());

        assertThatThrownBy(() -> correctDry(p, "800", CARD))
                .isInstanceOf(BusinessRuleException.class).hasMessage("El documento soporte ya esta anulado");
    }

    @Test
    void cannotCorrectAPurchaseWhoseInventoryAlreadyLeft() {
        DryCoffeePurchaseResponse p = dryPurchase();
        jdbc.update("UPDATE inventarios SET saldo_kilos = 1 WHERE modulo_compra = 'DRY_COFFEE' AND compra_id = ?", p.id());

        assertThatThrownBy(() -> correctDry(p, "800", CARD))
                .isInstanceOf(BusinessRuleException.class).hasMessage("La compra ya tiene salidas de inventario");
    }

    @Test
    void cannotCorrectAPurchaseInARemittance() {
        DryCoffeePurchaseResponse p = dryPurchase();
        Long remittance = jdbc.queryForObject("INSERT INTO remesas (agencia_id, fondo_id, numero_remesa, cantidad_empaques,"
                + " tipo_empaque, estado, creado_por_usuario_id, creado_en) VALUES (?, ?, 'SDBU-RP-9901', 10, 'USADO',"
                + " 'VIGENTE', ?, now()) RETURNING id", Long.class, AGENCY, rp, userId);
        jdbc.update("UPDATE compras_cafe_seco SET remesa_id = ? WHERE id = ?", remittance, p.id());

        assertThatThrownBy(() -> correctDry(p, "800", CARD))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("SDBU-RP-9901");
    }

    @Test
    void cannotCorrectAPurchaseClosedInAccess() {
        DryCoffeePurchaseResponse p = dryPurchase();
        jdbc.update("UPDATE compras_cafe_seco SET anulacion_bloqueada = true WHERE id = ?", p.id());

        assertThatThrownBy(() -> correctDry(p, "800", CARD)).isInstanceOf(BusinessRuleException.class)
                .hasMessage("IMPOSIBLE CORREGIR: ES UNA COMPRA CERRADA EN EL SISTEMA ANTERIOR.");
    }

    @Test
    void correctsToACheckWithANewAddressEvenIfThePaymentRowWasMissing() {
        DryCoffeePurchaseResponse p = dryPurchase();
        // Compras traidas de Access sin desglose de pago: la correccion lo escribe.
        jdbc.update("DELETE FROM formas_pago_compras WHERE origen = 'DRY' AND origen_id = ?", p.id());
        BigDecimal check = new BigDecimal("300000");
        DryCoffeePurchaseRequest typed = new DryCoffeePurchaseRequest(rp, "RN", SELLER, " VEREDA NUEVA ", "3105556677", 5,
                new BigDecimal("1000"), BigDecimal.ZERO, new BigDecimal("200"), new BigDecimal("10"), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, new PurchasePaymentRequest(p.netToPay().subtract(check), check,
                        BigDecimal.ZERO, BigDecimal.ZERO, " 0548 "));

        DryCoffeePurchaseResponse fixed = dry.correct(p.id(), typed);

        assertThat(fixed.address()).isEqualTo("VEREDA NUEVA");
        assertThat(fixed.cellphone()).isEqualTo("3105556677");
        assertThat(jdbc.queryForMap("SELECT cheque, numero_cheque FROM formas_pago_compras WHERE origen = 'DRY'"
                + " AND origen_id = ?", p.id())).containsEntry("numero_cheque", "0548");
        assertThat(receivables("DRY", p.id())).isEmpty();
    }

    @Test
    void cannotGoOverTheQuotaOfTheAnnouncement() {
        DryCoffeePurchaseResponse p = dryPurchase();

        assertThatThrownBy(() -> correctDry(p, "6000", CARD)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void adjustsTheBalanceOfTheFuturePurchaseByTheDifference() {
        DryCoffeePurchaseResponse p = dryPurchase();
        BigDecimal announced = new BigDecimal("2000");
        Long future = jdbc.queryForObject("INSERT INTO compras_anunciadas (creado_en, agencia_id, numero_anuncio,"
                + " fecha_anuncio, cedula, nombres, tipo, direccion, especial, kilos, saldo, fecha_entrega, pr_alm_sana,"
                + " pr_alm_defec, bonificacion, costos, pr_inc_calidad, pr_base_cps, creado_por_usuario_id)"
                + " VALUES (now(), ?, '1', CURRENT_DATE, ?, 'PRUEBA', 'C', 'VEREDA', 'NESPRESSO - FTUSA', ?, ?, CURRENT_DATE,"
                + " 0, 0, 0, 0, 0, 0, ?) RETURNING id", Long.class, AGENCY, SELLER, announced, announced.subtract(p.netKg()), userId);
        jdbc.update("UPDATE compras_cafe_seco SET compra_anunciada_id = ? WHERE id = ?", future, p.id());
        // Cada peticion carga la compra de la base; aca toda la prueba es una transaccion y JPA la tiene sin el enlace.
        entityManager.clear();

        DryCoffeePurchaseResponse fixed = correctDry(p, "800", CARD);

        assertThat(jdbc.queryForObject("SELECT saldo FROM compras_anunciadas WHERE id = ?", BigDecimal.class, future))
                .isEqualByComparingTo(announced.subtract(fixed.netKg()));
        // Mas kilos de los que quedan por entregar: se rechaza y no cambia nada.
        assertThatThrownBy(() -> correctDry(p, "3000", CARD)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void correctsGreenCoffee() {
        GreenCoffeePurchaseRequest preview = greenRequest("1000", null);
        var p = green.create(greenRequest("1000", cashAndCard(green.preview(preview).netToPay(), CARD)));

        BigDecimal net = green.previewCorrection(p.id(), greenRequest("700", null)).netToPay();
        var fixed = green.correct(p.id(), greenRequest("700", cashAndCard(net, CARD)));

        assertThat(fixed.invoiceNumber()).isEqualTo(p.invoiceNumber());
        assertThat(fixed.netKg()).isLessThan(p.netKg());
        assertThat(fixed.netToPay()).isEqualByComparingTo(net);
        assertThat((BigDecimal) inventory("GREEN_COFFEE", p.id()).get("kilos_netos")).isEqualByComparingTo(fixed.netKg());
        assertThat(jdbc.queryForObject("SELECT corregida FROM compras_cafe_verde WHERE id = ?", Boolean.class, p.id())).isTrue();
    }

    @Test
    void correctsHusk() {
        var p = husk.create(huskRequest("1000", cashAndCard(husk.preview(huskRequest("1000", null)).netToPay(), CARD)));

        BigDecimal net = husk.previewCorrection(p.id(), huskRequest("600", null)).netToPay();
        var fixed = husk.correct(p.id(), huskRequest("600", cashAndCard(net, CARD)));

        assertThat(fixed.invoiceNumber()).isEqualTo(p.invoiceNumber());
        assertThat(fixed.netKg()).isLessThan(p.netKg());
        assertThat(fixed.netToPay()).isEqualByComparingTo(net);
        assertThat((BigDecimal) inventory("HUSK", p.id()).get("kilos_netos")).isEqualByComparingTo(fixed.netKg());
        assertThat(jdbc.queryForObject("SELECT corregida FROM compras_pasilla WHERE id = ?", Boolean.class, p.id())).isTrue();
    }

    private GreenCoffeePurchaseRequest greenRequest(String grossKg, PurchasePaymentRequest payment) {
        return new GreenCoffeePurchaseRequest(rp, SELLER, null, null, 5, new BigDecimal(grossKg), BigDecimal.ZERO,
                BigDecimal.ZERO, new BigDecimal("12000"), BigDecimal.ZERO, BigDecimal.ZERO, payment);
    }

    private HuskPurchaseRequest huskRequest(String grossKg, PurchasePaymentRequest payment) {
        return new HuskPurchaseRequest(rp, SELLER, null, null, new BigDecimal("200"), 5, new BigDecimal(grossKg),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, payment);
    }
}
