package com.cafeoccidente.backend.supplies.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.common.security.Roles;
import com.cafeoccidente.backend.common.security.SecurityUser;
import com.cafeoccidente.backend.controlrecord.service.ControlRecordService;
import com.cafeoccidente.backend.purchases.annulment.repository.AnnulmentModule;
import com.cafeoccidente.backend.purchases.annulment.service.impl.PurchaseAnnulmentServiceImpl;
import com.cafeoccidente.backend.purchases.drycoffee.dto.DryCoffeePurchaseRequest;
import com.cafeoccidente.backend.purchases.drycoffee.dto.DryCoffeePurchaseResponse;
import com.cafeoccidente.backend.purchases.drycoffee.service.DryCoffeePurchaseService;
import com.cafeoccidente.backend.purchases.greencoffee.dto.GreenCoffeePurchaseRequest;
import com.cafeoccidente.backend.purchases.greencoffee.service.GreenCoffeePurchaseService;
import com.cafeoccidente.backend.purchases.husk.dto.HuskPurchaseRequest;
import com.cafeoccidente.backend.purchases.husk.service.HuskPurchaseService;
import com.cafeoccidente.backend.purchases.shared.dto.PurchasePaymentRequest;
import com.cafeoccidente.backend.purchases.shared.repository.AgencyRepository;
import com.cafeoccidente.backend.users.entity.User;
import com.cafeoccidente.backend.users.repository.RoleRepository;
import com.cafeoccidente.backend.users.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
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
 * 6e (HU-21, Q4, Q33, Q35): a donde va cada forma de pago de una compra, sobre la semilla (Buesaco 1),
 * asi que corre tambien en CI. Transaccional: no deja nada.
 * <ul>
 *   <li>Efectivo: saldo de Caja.</li>
 *   <li>Cheque: listado en Caja (Relacion de cheques) sin bajar su saldo; saldo de Suministros.</li>
 *   <li>Transferencia: listada en Caja; saldo de Suministros (Q33).</li>
 *   <li>Datafono: listado en Caja; cuenta por cobrar PENDIENTE a favor del fiel (Q33, Q35).</li>
 * </ul>
 */
@SpringBootTest
@Transactional
@SuppressWarnings("null")
class PurchasePaymentsIntegrationTest {

    private static final String SELLER = "990000555";
    private static final long AGENCY = 1L;
    private static final BigDecimal CHECK = new BigDecimal("300000");
    private static final BigDecimal TRANSFER = new BigDecimal("200000");
    private static final BigDecimal CARD = new BigDecimal("100000");

    @Autowired
    private DryCoffeePurchaseService dry;
    @Autowired
    private GreenCoffeePurchaseService green;
    @Autowired
    private HuskPurchaseService husk;
    @Autowired
    private SuppliesReportServiceImpl supplies;
    @Autowired
    private PurchaseAnnulmentServiceImpl annulment;
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
    void pointWithAnnouncementsAndASeller() {
        User user = new User();
        user.setUsername("prueba_6e");
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
                + " asociacion) VALUES (?, 'PRUEBA', 'PAGOS', ?, 'VEREDA', '3001112233', 'C', false)", SELLER, AGENCY);
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    private static PurchasePaymentRequest mixed(BigDecimal net) {
        return new PurchasePaymentRequest(net.subtract(CHECK).subtract(TRANSFER).subtract(CARD), CHECK, TRANSFER, CARD, "4321");
    }

    private DryCoffeePurchaseRequest dryRequest(PurchasePaymentRequest payment) {
        return new DryCoffeePurchaseRequest(rp, "RN", SELLER, null, null, 5, new BigDecimal("1000"), BigDecimal.ZERO,
                new BigDecimal("200"), new BigDecimal("10"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, payment);
    }

    private BigDecimal dryNet() {
        return dry.preview(dryRequest(null)).netToPay();
    }

    private List<SuppliesReportServiceImpl.Line> lines(List<SuppliesReportServiceImpl.Line> book, Integer invoice) {
        return book.stream().filter(l -> l.transactionId().equals(String.valueOf(invoice))).toList();
    }

    private List<Map<String, Object>> receivables(String module, Long purchaseId) {
        return jdbc.queryForList("SELECT estado, valor, cedula_vendedor, cedula_fiel FROM cuentas_por_cobrar"
                + " WHERE modulo_compra = ? AND compra_id = ?", module, purchaseId);
    }

    @Test
    void eachPaymentMethodGoesToItsPlaceAndNothingIsCountedTwice() {
        BigDecimal net = dryNet();
        DryCoffeePurchaseResponse saved = dry.create(dryRequest(mixed(net)));
        LocalDate today = LocalDate.now();

        // Listado de Caja (informe por forma de pago): las 4 formas, como Access.
        List<SuppliesReportServiceImpl.Line> cash = lines(supplies.cashBook(AGENCY, today, today), saved.invoiceNumber());
        assertThat(cash).extracting(SuppliesReportServiceImpl.Line::paymentMethod)
                .containsExactlyInAnyOrder("EFECTIVO", "CHEQUE", "TRANSFERENCIA", "DATAFONO");
        // Saldo de Caja: solo el efectivo.
        BigDecimal cashBalance = supplies.cashMovement(AGENCY, "RP", today).stream()
                .filter(r -> r.transactionId().equals(String.valueOf(saved.invoiceNumber())))
                .map(r -> r.outflow()).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal expectedCash = net.subtract(CHECK).subtract(TRANSFER).subtract(CARD);
        assertThat(cashBalance).isEqualByComparingTo(expectedCash);
        // Saldo de Suministros: cheque + transferencia (Q33).
        List<SuppliesReportServiceImpl.Line> supply = lines(supplies.supplyBook(AGENCY, today, today), saved.invoiceNumber());
        assertThat(supply).extracting(SuppliesReportServiceImpl.Line::paymentMethod)
                .containsExactlyInAnyOrder("CHEQUE", "TRANSFERENCIA");
        BigDecimal supplyOut = supply.stream().map(SuppliesReportServiceImpl.Line::outflow).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(supplyOut).isEqualByComparingTo(CHECK.add(TRANSFER));
        // Datafono: cuenta por cobrar PENDIENTE a favor del fiel del Registro de Control.
        List<Map<String, Object>> receivable = receivables("DRY", saved.id());
        assertThat(receivable).hasSize(1);
        assertThat(receivable.get(0)).containsEntry("estado", "PENDIENTE").containsEntry("cedula_vendedor", SELLER)
                .containsEntry("cedula_fiel", controlRecords.getActive(AGENCY).getTrustedId());
        BigDecimal card = (BigDecimal) receivable.get(0).get("valor");
        assertThat(card).isEqualByComparingTo(CARD);
        // Sin doble conteo: Caja + Suministros + cuenta por cobrar = neto a pagar.
        assertThat(cashBalance.add(supplyOut).add(card)).isEqualByComparingTo(saved.netToPay());
    }

    @Test
    void aPaymentThatDoesNotMatchTheNetIsRejected() {
        BigDecimal net = dryNet();

        for (BigDecimal total : new BigDecimal[] {net.add(BigDecimal.ONE), net.subtract(BigDecimal.ONE)}) {
            assertThatThrownBy(() -> dry.create(dryRequest(mixed(total)))).isInstanceOf(BusinessRuleException.class);
        }
        assertThatThrownBy(() -> dry.create(dryRequest(new PurchasePaymentRequest(
                net.subtract(CHECK), CHECK, BigDecimal.ZERO, BigDecimal.ZERO, " "))))
                .isInstanceOf(BusinessRuleException.class);

        // Que no consuma factura se ve en PurchaseInventoryRollbackIntegrationTest (aca la transaccion es la del test).
        assertThat(jdbc.queryForObject("SELECT count(*) FROM compras_cafe_seco WHERE cedula = ?", Long.class, SELLER)).isZero();
    }

    @Test
    void cashOnlyLeavesNoReceivable() {
        BigDecimal net = dryNet();
        DryCoffeePurchaseResponse saved = dry.create(dryRequest(
                new PurchasePaymentRequest(net, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null)));

        assertThat(receivables("DRY", saved.id())).isEmpty();
    }

    @Test
    void annullingRemovesItFromCashAndSuppliesAndAnnulsTheReceivable() {
        DryCoffeePurchaseResponse saved = dry.create(dryRequest(mixed(dryNet())));
        LocalDate today = LocalDate.now();

        annulment.annul(AGENCY, AnnulmentModule.DRY, saved.id());

        assertThat(lines(supplies.cashBook(AGENCY, today, today), saved.invoiceNumber())).isEmpty();
        assertThat(lines(supplies.supplyBook(AGENCY, today, today), saved.invoiceNumber())).isEmpty();
        assertThat(receivables("DRY", saved.id())).singleElement()
                .satisfies(r -> assertThat(r).containsEntry("estado", "ANULADA"));
    }

    @Test
    void greenAndHuskAlsoLeaveTheCardAsReceivable() {
        GreenCoffeePurchaseRequest greenPreview = new GreenCoffeePurchaseRequest(rp, SELLER, null, null, 5,
                new BigDecimal("1000"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("12000"), BigDecimal.ZERO,
                BigDecimal.ZERO, null);
        BigDecimal greenNet = green.preview(greenPreview).netToPay();
        var greenSaved = green.create(new GreenCoffeePurchaseRequest(rp, SELLER, null, null, 5, new BigDecimal("1000"),
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("12000"), BigDecimal.ZERO, BigDecimal.ZERO, mixed(greenNet)));

        HuskPurchaseRequest huskPreview = new HuskPurchaseRequest(rp, SELLER, null, null, new BigDecimal("200"), 5,
                new BigDecimal("1000"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null);
        BigDecimal huskNet = husk.preview(huskPreview).netToPay();
        var huskSaved = husk.create(new HuskPurchaseRequest(rp, SELLER, null, null, new BigDecimal("200"), 5,
                new BigDecimal("1000"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, mixed(huskNet)));

        assertThat(receivables("GREEN", greenSaved.id())).singleElement()
                .satisfies(r -> assertThat((BigDecimal) r.get("valor")).isEqualByComparingTo(CARD));
        assertThat(receivables("HUSK", huskSaved.id())).singleElement()
                .satisfies(r -> assertThat((BigDecimal) r.get("valor")).isEqualByComparingTo(CARD));
    }
}
