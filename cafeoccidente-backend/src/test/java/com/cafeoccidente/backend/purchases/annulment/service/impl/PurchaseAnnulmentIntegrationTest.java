package com.cafeoccidente.backend.purchases.annulment.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.common.exception.ResourceNotFoundException;
import com.cafeoccidente.backend.common.security.Roles;
import com.cafeoccidente.backend.common.security.SecurityUser;
import com.cafeoccidente.backend.purchases.annulment.dto.AnnulmentCandidate;
import com.cafeoccidente.backend.purchases.annulment.repository.AnnulmentDirection;
import com.cafeoccidente.backend.purchases.annulment.repository.AnnulmentModule;
import com.cafeoccidente.backend.purchases.drycoffee.dto.DryCoffeePurchaseRequest;
import com.cafeoccidente.backend.purchases.drycoffee.dto.DryCoffeePurchaseResponse;
import com.cafeoccidente.backend.purchases.drycoffee.service.DryCoffeePurchaseService;
import com.cafeoccidente.backend.purchases.future.service.AnnouncementQuotaService;
import com.cafeoccidente.backend.purchases.shared.dto.PurchasePaymentRequest;
import com.cafeoccidente.backend.purchases.shared.repository.AgencyRepository;
import com.cafeoccidente.backend.supplies.dto.SuppliesReportRow;
import com.cafeoccidente.backend.supplies.service.SuppliesReportService;
import com.cafeoccidente.backend.users.entity.User;
import com.cafeoccidente.backend.users.repository.RoleRepository;
import com.cafeoccidente.backend.users.repository.UserRepository;
import com.cafeoccidente.backend.vendors.repository.VendorReadRepository;
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
 * 6f (HU-25, RF-23) sobre la semilla (agencia 1), asi corre tambien en CI. Cada compra se guarda con el
 * servicio real: formas de pago, inventario y cuenta por cobrar del datafono. Transaccional: no deja nada.
 */
@SpringBootTest
@Transactional
@SuppressWarnings("null")
class PurchaseAnnulmentIntegrationTest {

    private static final String SELLER = "990000777";
    private static final long AGENCY = 1L;
    private static final BigDecimal CHECK = new BigDecimal("300000");
    private static final BigDecimal TRANSFER = new BigDecimal("200000");
    private static final BigDecimal CARD = new BigDecimal("100000");
    private static final BigDecimal QUOTA = new BigDecimal("5000");

    @Autowired
    private PurchaseAnnulmentServiceImpl service;
    @Autowired
    private DryCoffeePurchaseService dry;
    @Autowired
    private AnnouncementQuotaService quotas;
    @Autowired
    private SuppliesReportService supplies;
    @Autowired
    private VendorReadRepository vendors;
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
    private Integer announcementNumber;

    @BeforeEach
    void pointWithAnAnnouncementWithQuotaAndASeller() {
        User user = new User();
        user.setUsername("prueba_6f");
        user.setPasswordHash("x");
        user.setRole(roles.findByName(Roles.ADMINISTRADOR).orElseThrow());
        user.setAgency(agencies.findById(AGENCY).orElseThrow());
        SecurityUser principal = new SecurityUser(users.save(user));
        userId = principal.getUserId();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        rp = jdbc.queryForObject("SELECT id FROM fondos WHERE codigo = 'RP'", Long.class);
        Long announcement = jdbc.queryForObject("INSERT INTO anuncios (fecha_anuncio, pr_base_cps, pr_alm_defec,"
                + " sobrepr_carga_esp, pr_inc_calidad, fondo_id, especial, activo) VALUES (CURRENT_DATE, 1546900, 6000, 80000,"
                + " 50, ?, 'RN', true) RETURNING id", Long.class, rp);
        Map<String, Object> numbering = jdbc.queryForMap("INSERT INTO anuncios_agencias (agencia_id, anuncio_id,"
                + " numero_anuncio, fecha_asignacion) SELECT ?, ?, COALESCE(MAX(numero_anuncio), 0) + 1, CURRENT_DATE"
                + " FROM anuncios_agencias WHERE agencia_id = ? RETURNING id, numero_anuncio", AGENCY, announcement, AGENCY);
        announcementNumber = ((Number) numbering.get("numero_anuncio")).intValue();
        jdbc.update("INSERT INTO cupos_anuncios (anuncio_agencia_id, cupo) VALUES (?, ?)", numbering.get("id"), QUOTA);
        jdbc.update("INSERT INTO terceros (identificacion, primer_nombre, primer_apellido, agencia_id, direccion, telefono, tipo,"
                + " asociacion) VALUES (?, 'PRUEBA', 'ANULACION', ?, 'VEREDA', '3001112233', 'C', false)", SELLER, AGENCY);
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    /** Compra de cafe seco con pago mixto: efectivo, cheque, transferencia y datafono. */
    private DryCoffeePurchaseResponse purchase() {
        DryCoffeePurchaseRequest preview = request(null);
        BigDecimal net = dry.preview(preview).netToPay();
        return dry.create(request(new PurchasePaymentRequest(
                net.subtract(CHECK).subtract(TRANSFER).subtract(CARD), CHECK, TRANSFER, CARD, "4321")));
    }

    private DryCoffeePurchaseRequest request(PurchasePaymentRequest payment) {
        return new DryCoffeePurchaseRequest(rp, "RN", SELLER, null, null, 5, new BigDecimal("1000"), BigDecimal.ZERO,
                new BigDecimal("200"), new BigDecimal("10"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, payment);
    }

    private AnnulmentCandidate annul(DryCoffeePurchaseResponse p) {
        return service.annul(AGENCY, AnnulmentModule.DRY, p.id());
    }

    private String status(DryCoffeePurchaseResponse p) {
        return jdbc.queryForObject("SELECT estado FROM compras_cafe_seco WHERE id = ?", String.class, p.id());
    }

    private void assertRejected(DryCoffeePurchaseResponse p, String message) {
        assertThatThrownBy(() -> annul(p)).isInstanceOf(BusinessRuleException.class).hasMessage(message);
        assertThat(status(p)).isEqualTo("VALIDA");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventarios WHERE modulo_compra = 'DRY_COFFEE'"
                + " AND compra_id = ?", Long.class, p.id())).isOne();
    }

    @Test
    void ca2AnnulsThePurchaseAndRevertsEverythingItLeft() {
        DryCoffeePurchaseResponse p = purchase();
        Map<String, Object> before = jdbc.queryForMap("SELECT * FROM compras_cafe_seco WHERE id = ?", p.id());

        AnnulmentCandidate annulled = annul(p);

        assertThat(annulled.status()).isEqualTo("ANULADA");
        assertThat(annulled.netToPay()).isZero();
        assertThat(annulled.netKg()).isZero();
        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM compras_cafe_seco WHERE id = ?", p.id());
        for (String zero : List.of("sacos", "kilos_brutos", "kilos_netos", "vr_bruto", "vr_inventario", "retefuente",
                "factor", "pr_alm_sana", "costos", "descuento_cooperativo", "aporte_asociado", "neto_a_pagar")) {
            assertThat(((Number) row.get(zero)).doubleValue()).as(zero).isZero();
        }
        // AnularFactura no toca W_TotAlm ni Pr_Base_CPS; la factura conserva su numero.
        assertThat((BigDecimal) row.get("peso_tot_alm")).isEqualByComparingTo((BigDecimal) before.get("peso_tot_alm"));
        assertThat((BigDecimal) row.get("pr_base_cps")).isEqualByComparingTo((BigDecimal) before.get("pr_base_cps"));
        assertThat(row.get("numero_factura")).isEqualTo(p.invoiceNumber());
        assertThat(row.get("numero_cheque")).isNull();
        // Formas de pago en 0, inventario borrado y cuenta por cobrar del datafono ANULADA.
        assertThat(jdbc.queryForObject("SELECT efectivo + cheque + transferencia + datafono"
                + " FROM formas_pago_compras WHERE origen = 'DRY' AND origen_id = ?", BigDecimal.class, p.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventarios WHERE modulo_compra = 'DRY_COFFEE'"
                + " AND compra_id = ?", Long.class, p.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT estado FROM cuentas_por_cobrar WHERE modulo_compra = 'DRY' AND compra_id = ?",
                String.class, p.id())).isEqualTo("ANULADA");
        // Caja y Suministros se arman de las formas de pago: ya no la muestran.
        LocalDate today = LocalDate.now();
        String invoice = String.valueOf(p.invoiceNumber());
        assertThat(supplies.paymentMethods(AGENCY, today, today, "*")).extracting(SuppliesReportRow::transactionId)
                .doesNotContain(invoice);
        for (String fund : List.of("RP", "LF")) {
            assertThat(supplies.supplies(AGENCY, fund, today)).extracting(SuppliesReportRow::transactionId)
                    .doesNotContain(invoice);
        }
        assertThat(vendors.beneficiary(AGENCY, SELLER, null, null)).singleElement()
                .satisfies(b -> assertThat(b.netToPay()).isZero());
    }

    @Test
    void ca3GivesTheKilosBackToTheQuotaOfTheAnnouncement() {
        DryCoffeePurchaseResponse p = purchase();
        BigDecimal kg = p.netKg();
        assertThat(quotas.get(AGENCY, announcementNumber).balance()).isEqualByComparingTo(QUOTA.subtract(kg));

        annul(p);

        assertThat(quotas.get(AGENCY, announcementNumber).deliveredKg()).isZero();
        assertThat(quotas.get(AGENCY, announcementNumber).balance()).isEqualByComparingTo(QUOTA);
    }

    @Test
    void ca3GivesTheKilosBackToTheFuturePurchase() {
        DryCoffeePurchaseResponse p = purchase();
        BigDecimal announced = new BigDecimal("2000");
        Long future = jdbc.queryForObject("INSERT INTO compras_anunciadas (creado_en, agencia_id, numero_anuncio,"
                + " fecha_anuncio, cedula, nombres, tipo, direccion, especial, kilos, saldo, fecha_entrega, pr_alm_sana,"
                + " pr_alm_defec, bonificacion, costos, pr_inc_calidad, pr_base_cps, creado_por_usuario_id)"
                + " VALUES (now(), ?, '1', CURRENT_DATE, ?, 'PRUEBA', 'C', 'VEREDA', 'NESPRESSO - FTUSA', ?, ?, CURRENT_DATE,"
                + " 0, 0, 0, 0, 0, 0, ?) RETURNING id", Long.class, AGENCY, SELLER, announced, announced.subtract(p.netKg()), userId);
        jdbc.update("UPDATE compras_cafe_seco SET compra_anunciada_id = ? WHERE id = ?", future, p.id());

        annul(p);

        assertThat(jdbc.queryForObject("SELECT saldo FROM compras_anunciadas WHERE id = ?", BigDecimal.class, future))
                .isEqualByComparingTo(announced);
    }

    @Test
    void theAnnulledInvoiceNumberIsNotReused() {
        DryCoffeePurchaseResponse annulled = purchase();
        annul(annulled);

        DryCoffeePurchaseResponse next = purchase();

        assertThat(next.invoiceNumber()).isEqualTo(annulled.invoiceNumber() + 1);
        assertThat(service.findByInvoice(AGENCY, annulled.invoiceNumber())).singleElement()
                .satisfies(c -> assertThat(c.status()).isEqualTo("ANULADA"));
    }

    @Test
    void aPurchaseClosedInAccessIsNotAnnulled() {
        DryCoffeePurchaseResponse p = purchase();
        jdbc.update("UPDATE compras_cafe_seco SET anulacion_bloqueada = true WHERE id = ?", p.id());

        assertRejected(p, PurchaseAnnulmentServiceImpl.ANNULMENT_LOCKED);
    }

    @Test
    void aPurchaseWhoseInventoryAlreadyLeftIsNotAnnulled() {
        DryCoffeePurchaseResponse p = purchase();
        jdbc.update("UPDATE inventarios SET saldo_kilos = 1 WHERE modulo_compra = 'DRY_COFFEE' AND compra_id = ?", p.id());

        assertRejected(p, PurchaseAnnulmentServiceImpl.HAS_INVENTORY_EXITS);
    }

    @Test
    void aPurchaseWhoseReceivableIsNoLongerPendingIsNotAnnulled() {
        DryCoffeePurchaseResponse p = purchase();
        // Q35: hoy no hay estado "saldada"; cualquier estado distinto de PENDIENTE bloquea.
        jdbc.update("UPDATE cuentas_por_cobrar SET estado = 'ANULADA' WHERE modulo_compra = 'DRY' AND compra_id = ?", p.id());

        assertRejected(p, PurchaseAnnulmentServiceImpl.RECEIVABLE_NOT_PENDING);
    }

    @Test
    void cannotAnnulTwiceNorFromAnotherAgency() {
        DryCoffeePurchaseResponse p = purchase();
        assertThatThrownBy(() -> service.annul(AGENCY + 1000, AnnulmentModule.DRY, p.id()))
                .isInstanceOf(ResourceNotFoundException.class);
        annul(p);
        assertThatThrownBy(() -> annul(p))
                .isInstanceOf(BusinessRuleException.class).hasMessage(PurchaseAnnulmentServiceImpl.ALREADY_ANNULLED);
    }

    @Test
    void ca1WalksThePurchasesOfTheWorkingAgencyOneByOne() {
        DryCoffeePurchaseResponse first = purchase();
        DryCoffeePurchaseResponse second = purchase();
        Integer lowest = jdbc.queryForObject("SELECT min(numero_factura) FROM (SELECT numero_factura FROM compras_cafe_seco"
                + " WHERE agencia_id = ? UNION ALL SELECT numero_factura FROM compras_cafe_verde WHERE agencia_id = ?"
                + " UNION ALL SELECT numero_factura FROM compras_pasilla WHERE agencia_id = ?) f", Integer.class,
                AGENCY, AGENCY, AGENCY);
        // Otra agencia con un numero mayor: no aparece al recorrer la agencia de trabajo.
        jdbc.update("INSERT INTO compras_cafe_seco (fecha_compra, agencia_id, fondo_id, especial, producto_id,"
                + " numero_anuncio, fecha_anuncio, pr_base_cps, cedula, nombres, apellidos, tipo_vendedor, direccion,"
                + " celular, sacos, kilos_brutos, destare, kilos_netos, peso_tot_alm, porc_merma, peso_tot_pasilla,"
                + " porc_kg_pasilla, peso_alm_sana, factor, pr_alm_sana, pr_alm_defec, bonificacion, castigo, costos,"
                + " vr_kilo, vr_bruto, vr_inventario, aporte_asociado, descuento_cooperativo, retefuente,"
                + " descuento_financiero, otros_descuentos, neto_a_pagar, forma_pago, creado_por_usuario_id, creado_en,"
                + " numero_factura) SELECT fecha_compra, (SELECT min(id) FROM agencias WHERE id <> agencia_id), fondo_id,"
                + " especial, producto_id, numero_anuncio, fecha_anuncio, pr_base_cps, cedula, nombres, apellidos,"
                + " tipo_vendedor, direccion, celular, sacos, kilos_brutos, destare, kilos_netos, peso_tot_alm, porc_merma,"
                + " peso_tot_pasilla, porc_kg_pasilla, peso_alm_sana, factor, pr_alm_sana, pr_alm_defec, bonificacion,"
                + " castigo, costos, vr_kilo, vr_bruto, vr_inventario, aporte_asociado, descuento_cooperativo, retefuente,"
                + " descuento_financiero, otros_descuentos, neto_a_pagar, forma_pago, creado_por_usuario_id, creado_en,"
                + " numero_factura + 1 FROM compras_cafe_seco WHERE id = ?", second.id());

        assertThat(invoices(service.navigate(AGENCY, first.invoiceNumber(), AnnulmentDirection.NEXT)))
                .containsExactly(second.invoiceNumber());
        assertThat(invoices(service.navigate(AGENCY, second.invoiceNumber(), AnnulmentDirection.PREV)))
                .containsExactly(first.invoiceNumber());
        assertThat(invoices(service.navigate(AGENCY, null, AnnulmentDirection.LAST))).containsExactly(second.invoiceNumber());
        assertThat(invoices(service.navigate(AGENCY, second.invoiceNumber(), AnnulmentDirection.FIRST))).containsExactly(lowest);
        assertThat(service.navigate(AGENCY, second.invoiceNumber(), AnnulmentDirection.NEXT)).isEmpty();
    }

    private static List<Integer> invoices(List<AnnulmentCandidate> candidates) {
        return candidates.stream().map(AnnulmentCandidate::invoiceNumber).toList();
    }
}
