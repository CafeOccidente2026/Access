package com.cafeoccidente.backend.purchases.remittance.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.common.exception.ResourceNotFoundException;
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
import com.cafeoccidente.backend.inventory.dto.RemissionLineRequest;
import com.cafeoccidente.backend.inventory.dto.RemissionRequest;
import com.cafeoccidente.backend.inventory.service.RemissionService;
import com.cafeoccidente.backend.purchases.remittance.dto.RemittanceFiles;
import com.cafeoccidente.backend.purchases.remittance.dto.RemittanceLine;
import com.cafeoccidente.backend.purchases.remittance.dto.RemittanceResponse;
import com.cafeoccidente.backend.purchases.remittance.service.RemittanceService;
import com.cafeoccidente.backend.purchases.shared.dto.PurchasePaymentRequest;
import com.cafeoccidente.backend.purchases.shared.repository.AgencyRepository;
import com.cafeoccidente.backend.users.entity.User;
import com.cafeoccidente.backend.users.repository.RoleRepository;
import com.cafeoccidente.backend.users.repository.UserRepository;
import java.math.BigDecimal;
import java.math.MathContext;
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
 * 7b (HU-13 / RF-11, HU-14 / RF-12): Genera Remesa sobre la semilla (agencia 1) con datos inventados. Las
 * compras de seco, verde y pasilla pendientes del fondo se agrupan en una remesa numerada con el consecutivo
 * de remision, salen de pendientes, registran la salida de empaques y dan las filas de los 4 archivos como
 * las consultas de Access (Genera Plano, ParaOperaciones, ParaOpen, ParaAlmacafe). Transaccional.
 */
@SpringBootTest
@Transactional
@SuppressWarnings("null")
class RemittanceIntegrationTest {

    private static final String SELLER = "990007002";
    private static final long AGENCY = 1L;

    @Autowired
    private RemittanceService remittances;
    @Autowired
    private PurchaseAnnulmentService annulment;
    @Autowired
    private RemissionService remissions;
    @Autowired
    private DryCoffeePurchaseService dry;
    @Autowired
    private GreenCoffeePurchaseService green;
    @Autowired
    private HuskPurchaseService husk;
    @Autowired
    private UserRepository users;
    @Autowired
    private RoleRepository roles;
    @Autowired
    private AgencyRepository agencies;
    @Autowired
    private JdbcTemplate jdbc;

    private Long rp;

    /** Una compra de un modulo (las tres tablas de compras). */
    private record Ref(AnnulmentModule module, Long id) {
    }

    private SecurityUser signIn(String role) {
        User user = new User();
        user.setUsername("prueba_7b_" + role.toLowerCase());
        user.setPasswordHash("x");
        user.setRole(roles.findByName(role).orElseThrow());
        user.setAgency(agencies.findById(AGENCY).orElseThrow());
        SecurityUser principal = new SecurityUser(users.save(user));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        return principal;
    }

    @BeforeEach
    void pointWithAnnouncementsAndASeller() {
        signIn(Roles.USUARIO);
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
        jdbc.update("INSERT INTO terceros (identificacion, primer_nombre, segundo_nombre, primer_apellido, agencia_id,"
                + " direccion, telefono, tipo, asociacion) VALUES (?, 'PRUEBA', 'UNO', 'REMESA', ?, 'VEREDA', '3001112233',"
                + " 'C', false)", SELLER, AGENCY);
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    private static PurchasePaymentRequest cash(BigDecimal net) {
        return new PurchasePaymentRequest(net, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null);
    }

    private Ref dryPurchase() {
        DryCoffeePurchaseRequest request = new DryCoffeePurchaseRequest(rp, "RN", SELLER, null, null, 5,
                new BigDecimal("1000"), BigDecimal.ZERO, new BigDecimal("200"), new BigDecimal("10"), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, null);
        BigDecimal net = dry.preview(request).netToPay();
        return new Ref(AnnulmentModule.DRY, dry.create(new DryCoffeePurchaseRequest(rp, "RN", SELLER,
                null, null, 5, new BigDecimal("1000"), BigDecimal.ZERO, new BigDecimal("200"), new BigDecimal("10"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, cash(net))).id());
    }

    private Ref greenPurchase() {
        GreenCoffeePurchaseRequest request = new GreenCoffeePurchaseRequest(rp, SELLER, null, null, 5,
                new BigDecimal("1000"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("12000"), BigDecimal.ZERO,
                BigDecimal.ZERO, null);
        BigDecimal net = green.preview(request).netToPay();
        return new Ref(AnnulmentModule.GREEN, green.create(new GreenCoffeePurchaseRequest(rp, SELLER,
                null, null, 5, new BigDecimal("1000"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("12000"),
                BigDecimal.ZERO, BigDecimal.ZERO, cash(net))).id());
    }

    private Ref huskPurchase() {
        HuskPurchaseRequest request = new HuskPurchaseRequest(rp, SELLER, null, null, new BigDecimal("200"), 5,
                new BigDecimal("1000"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null);
        BigDecimal net = husk.preview(request).netToPay();
        return new Ref(AnnulmentModule.HUSK, husk.create(new HuskPurchaseRequest(rp, SELLER, null,
                null, new BigDecimal("200"), 5, new BigDecimal("1000"), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, cash(net))).id());
    }

    /** Una remesa vieja, sin remision (de Access o de Genera Remesa): la remesa, sus compras y su salida de empaques. */
    private RemittanceResponse remit(String special, List<Ref> purchases) {
        // Con el modulo: en una base nueva el seco, el verde y la pasilla pueden tener el mismo id.
        String number = "SDBU-RP-9" + purchases.get(0).module().name().charAt(0) + purchases.get(0).id();
        Long id = jdbc.queryForObject("INSERT INTO remesas (agencia_id, fondo_id, numero_remesa, especial_filtro,"
                + " cantidad_empaques, tipo_empaque, estado, creado_por_usuario_id, creado_en) VALUES (?, ?, ?, ?, 15,"
                + " 'USADO', 'VIGENTE', (SELECT id FROM usuarios WHERE usuario = 'prueba_7b_usuario'), now()) RETURNING id",
                Long.class, AGENCY, rp, number, special);
        purchases.forEach(p -> jdbc.update("UPDATE " + p.module().table() + " SET remesa_id = ? WHERE id = ?", id, p.id()));
        jdbc.update("INSERT INTO empaques (id_transaccion, agencia_id, tipo_empaque, fecha, cedula, detalle, entradas,"
                + " salidas, creado_en) VALUES (?, ?, 'USADO', CURRENT_DATE, '1', 'Remision', 0, 15, now())", number, AGENCY);
        return remittances.findByNumber(number).get(0);
    }

    private Long remittanceOf(Ref purchase) {
        return jdbc.queryForObject("SELECT remesa_id FROM " + purchase.module().table() + " WHERE id = ?", Long.class,
                purchase.id());
    }

    @Test
    void theFilesNameTheCoffeeTypeOfThePurchases() {
        // Nombres de archivo del 24-nov: <remision><SECO|VERDE|PASILLA>.
        assertThat(remittances.files(remit(null, List.of(dryPurchase())).id()).coffeeType()).isEqualTo("SECO");
        assertThat(remittances.files(remit(null, List.of(greenPurchase())).id()).coffeeType()).isEqualTo("VERDE");
        assertThat(remittances.files(remit(null, List.of(huskPurchase())).id()).coffeeType()).isEqualTo("PASILLA");
    }

    @Test
    void theFilesCarryTheAccessColumnsOfEachPurchase() {
        Ref d = dryPurchase();
        Ref h = huskPurchase();
        RemittanceResponse remittance = remit(null, List.of(d, h));

        RemittanceFiles files = remittances.files(remittance.id());

        assertThat(files.number()).isEqualTo(remittance.number());
        assertThat(files.generatedAt()).isNotNull();
        // Una remesa vieja de Access puede mezclar seco y pasilla: van los dos tipos.
        assertThat(files.coffeeType()).isEqualTo("SECO-PASILLA");
        assertThat(files.lines()).hasSize(2);
        Map<String, Object> seco = jdbc.queryForMap("SELECT * FROM compras_cafe_seco WHERE id = ?", d.id());
        BigDecimal sample = jdbc.queryForObject("SELECT tamano_muestra FROM registros_control WHERE agencia_id = ?"
                + " AND activo", BigDecimal.class, AGENCY);
        RemittanceLine line = files.lines().stream().filter(l -> l.specialType().equals("RN")).findFirst().orElseThrow();
        BigDecimal kg = (BigDecimal) seco.get("kilos_netos");
        BigDecimal factor = (BigDecimal) seco.get("factor");
        assertThat(line.invoice()).isEqualTo("SDBU" + seco.get("numero_factura"));
        assertThat(line.idNumber()).isEqualTo(SELLER);
        assertThat(line.sellerName()).isEqualTo("PRUEBA UNO REMESA");
        assertThat(line.fundCode()).isEqualTo("RP");
        assertThat(line.netKg()).isEqualByComparingTo(kg);
        assertThat(line.factor()).isEqualByComparingTo(factor);
        // ParaOperaciones: KAS = cantidad x PORC_AS, sin dividir por 100, como Access.
        assertThat(line.kas()).isEqualByComparingTo(kg.multiply(factor));
        assertThat(line.grossValue()).isEqualByComparingTo((BigDecimal) seco.get("vr_bruto"));
        assertThat(line.unitPrice()).isEqualByComparingTo((BigDecimal) seco.get("vr_kilo"));
        assertThat(line.healthyPrice()).isEqualByComparingTo((BigDecimal) seco.get("pr_alm_sana"));
        assertThat(line.bonus()).isEqualByComparingTo((BigDecimal) seco.get("bonificacion"));
        // GeneraPlano PORC_AS = W_AlmSana / Muestra x 100.
        assertThat(line.healthyPercentage()).isEqualByComparingTo(((BigDecimal) seco.get("peso_alm_sana"))
                .multiply(new BigDecimal("100")).divide(sample, MathContext.DECIMAL64));
        assertThat(line.huskPercentage()).isEqualByComparingTo((BigDecimal) seco.get("porc_kg_pasilla"));
        assertThat(line.wastePercentage()).isEqualByComparingTo((BigDecimal) seco.get("porc_merma"));
        assertThat(line.bonusTotal()).isEqualByComparingTo(((BigDecimal) seco.get("bonificacion")).multiply(kg));
        // PR_BASE_TOT es el Pr_Base_CPS del anuncio; SPR_NESS_TOT el precio base de carga de la compra.
        assertThat(line.announcementBasePrice()).isEqualByComparingTo("1546900");
        assertThat(line.purchaseBasePrice()).isEqualByComparingTo((BigDecimal) seco.get("pr_base_cps"));
        assertThat(line.announcementDate()).isEqualTo(((java.sql.Date) seco.get("fecha_anuncio")).toLocalDate());
        assertThat(line.purchaseDateTime()).isNotNull();

        // Pasilla (Form_PASILLA): % Almendra es su PorcAlmSana y Peso Almendra su W_AlmSana.
        Map<String, Object> pasilla = jdbc.queryForMap("SELECT * FROM compras_pasilla WHERE id = ?", h.id());
        RemittanceLine husks = files.lines().stream().filter(l -> l.specialType().equals("PASILLA")).findFirst().orElseThrow();
        assertThat(husks.factor()).isEqualByComparingTo((BigDecimal) pasilla.get("porc_almendra"));
        assertThat(husks.healthyPercentage()).isEqualByComparingTo(((BigDecimal) pasilla.get("peso_almendra"))
                .multiply(new BigDecimal("100")).divide(sample, MathContext.DECIMAL64));
        assertThat(husks.bonus()).isZero();
        assertThat(husks.wastePercentage()).isZero();
    }

    @Test
    void unmarkingFreesThePurchasesReturnsThePackagingAndLeavesHistory() {
        Ref d = dryPurchase();
        RemittanceResponse remittance = remit("RN", List.of(d));
        signIn(Roles.ADMINISTRADOR);

        RemittanceResponse unmarked = remittances.unmark(remittance.id());

        assertThat(unmarked.status()).isEqualTo("DESMARCADA");
        assertThat(unmarked.fromRemission()).isFalse();
        assertThat(remittanceOf(d)).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM empaques WHERE agencia_id = ? AND id_transaccion = ?",
                Integer.class, AGENCY, remittance.number())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM historial_cambios WHERE entidad = 'REMESA'"
                + " AND accion = 'DESMARCAR' AND entidad_id = ?", Integer.class, remittance.id())).isEqualTo(1);
        assertThatThrownBy(() -> remittances.unmark(remittance.id())).isInstanceOf(BusinessRuleException.class);
    }

    /** Demo del 23-nov: la remesa que genero una remision se deshace con Anular Remision, no con Desmarca Remesa. */
    @Test
    void aRemittanceOfARemissionIsNotUnmarked() {
        Ref d = dryPurchase();
        Long movement = jdbc.queryForObject("SELECT id FROM inventarios WHERE modulo_compra = 'DRY_COFFEE' AND compra_id = ?",
                Long.class, d.id());
        var remission = remissions.create(new RemissionRequest(java.time.LocalDate.now(), "ALMACAFE", null,
                List.of(new RemissionLineRequest(movement)), 5, "USADO"));
        signIn(Roles.ADMINISTRADOR);

        RemittanceResponse found = remittances.findByNumber(remission.displayNumber()).get(0);
        assertThat(found.fromRemission()).isTrue();
        assertThatThrownBy(() -> remittances.unmark(found.id())).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Anular Remisión");
        assertThat(remittanceOf(d)).isEqualTo(found.id());
    }

    @Test
    void aPurchaseInARemittanceCannotBeAnnulled() {
        Ref d = dryPurchase();
        RemittanceResponse remittance = remit("RN", List.of(d));

        assertThatThrownBy(() -> annulment.annul(AGENCY, AnnulmentModule.DRY, d.id()))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining(remittance.number());
    }

    @Test
    void aRemittanceOfAnotherAgencyIsNotFound() {
        RemittanceResponse remittance = remit("RN", List.of(dryPurchase()));
        jdbc.update("UPDATE remesas SET agencia_id = (SELECT min(id) FROM agencias WHERE id <> ?) WHERE id = ?",
                AGENCY, remittance.id());

        assertThat(remittances.findByNumber(remittance.number())).isEmpty();
        assertThatThrownBy(() -> remittances.files(remittance.id())).isInstanceOf(ResourceNotFoundException.class);
        // Revision del 24-nov: tampoco se puede desmarcar una remesa de otra agencia.
        assertThatThrownBy(() -> remittances.unmark(remittance.id())).isInstanceOf(ResourceNotFoundException.class);
    }
}
