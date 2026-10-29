package com.cafeoccidente.backend.purchases.shared.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.common.exception.ResourceNotFoundException;
import com.cafeoccidente.backend.common.security.Roles;
import com.cafeoccidente.backend.common.security.SecurityUser;
import com.cafeoccidente.backend.controlrecord.service.ControlRecordService;
import com.cafeoccidente.backend.purchases.drycoffee.dto.DryCoffeePurchaseRequest;
import com.cafeoccidente.backend.purchases.drycoffee.dto.DryCoffeePurchaseResponse;
import com.cafeoccidente.backend.purchases.drycoffee.service.DryCoffeePurchaseService;
import com.cafeoccidente.backend.purchases.future.dto.AnnouncementResponse;
import com.cafeoccidente.backend.purchases.future.service.AnnouncementService;
import com.cafeoccidente.backend.purchases.greencoffee.dto.GreenCoffeePurchaseRequest;
import com.cafeoccidente.backend.purchases.greencoffee.dto.GreenCoffeePurchaseResponse;
import com.cafeoccidente.backend.purchases.greencoffee.service.GreenCoffeePurchaseService;
import com.cafeoccidente.backend.purchases.husk.dto.HuskPurchaseRequest;
import com.cafeoccidente.backend.purchases.husk.dto.HuskPurchaseResponse;
import com.cafeoccidente.backend.purchases.husk.service.HuskPurchaseService;
import com.cafeoccidente.backend.purchases.shared.dto.PurchasePaymentRequest;
import com.cafeoccidente.backend.purchases.shared.repository.AgencyRepository;
import com.cafeoccidente.backend.purchases.shared.repository.GrowerDailyTotalsRepository;
import com.cafeoccidente.backend.purchases.shared.service.InvoiceConsecutiveService;
import com.cafeoccidente.backend.users.entity.User;
import com.cafeoccidente.backend.users.repository.RoleRepository;
import com.cafeoccidente.backend.users.repository.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
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
 * 6b: precios, bonificacion, costos y datos del vendedor los pone el servidor (anuncio vigente y
 * TERCEROS), nunca el navegador. Con sus propios anuncios y terceros sobre la semilla (Buesaco 1),
 * asi que corre tambien en CI. Transaccional: no deja nada.
 */
@SpringBootTest
@Transactional
@SuppressWarnings("null")
class PurchaseServerValuesIntegrationTest {

    private static final String COMPLETE = "990000111";
    private static final String NO_CONTACT = "990000222";
    private static final String DECEASED = "990000333";
    private static final long AGENCY = 1L;

    @Autowired
    private DryCoffeePurchaseService dry;
    @Autowired
    private GreenCoffeePurchaseService green;
    @Autowired
    private HuskPurchaseService husk;
    @Autowired
    private AnnouncementService announcements;
    @Autowired
    private InvoiceConsecutiveService consecutive;
    @Autowired
    private ControlRecordService controlRecords;
    @Autowired
    private UserRepository users;
    @Autowired
    private RoleRepository roles;
    @Autowired
    private AgencyRepository agencies;
    @Autowired
    private GrowerDailyTotalsRepository dailyTotals;
    @Autowired
    private JdbcTemplate jdbc;

    private Long rp;

    @BeforeEach
    void pointWithAnnouncementsAndGrowers() {
        User user = new User();
        user.setUsername("prueba_6b");
        user.setPasswordHash("x");
        user.setRole(roles.findByName(Roles.ADMINISTRADOR).orElseThrow());
        user.setAgency(agencies.findById(AGENCY).orElseThrow());
        SecurityUser principal = new SecurityUser(users.save(user));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        rp = jdbc.queryForObject("SELECT id FROM fondos WHERE codigo = 'RP'", Long.class);
        for (String special : new String[] {"RN", "CV", "PASILLA", "NESPRESSO - FTUSA", "NESPRESSO CON TAZA - FTUSA",
                "NESPRESSO LATE HARVEST CON TAZA - FTUSA"}) {
            Long id = jdbc.queryForObject("INSERT INTO anuncios (fecha_anuncio, pr_base_cps, pr_alm_defec, sobrepr_carga_esp,"
                    + " pr_inc_calidad, fondo_id, especial, activo) VALUES (CURRENT_DATE, 1546900, 6000, 80000, 50, ?, ?, true)"
                    + " RETURNING id", Long.class, rp, special);
            jdbc.update("INSERT INTO anuncios_agencias (agencia_id, anuncio_id, numero_anuncio, fecha_asignacion)"
                    + " SELECT ?, ?, COALESCE(MAX(numero_anuncio), 0) + 1, CURRENT_DATE FROM anuncios_agencias WHERE agencia_id = ?",
                    AGENCY, id, AGENCY);
        }
        // HU-23 CA-2: sin los 3 pesos de verde no se registra (Q23); la semilla los trae en 0.
        greenSample("140", "12.5", "186.18");
        grower(COMPLETE, "VEREDA EL ALTO", "3001112233", false);
        grower(NO_CONTACT, "", "", false);
        grower(DECEASED, "VEREDA BAJA", "3004445566", true);
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    private void greenSample(String total, String husk, String healthy) {
        jdbc.update("UPDATE registros_control SET peso_tot_alm_cv = ?::numeric, peso_tot_pasilla_cv = ?::numeric,"
                + " peso_alm_sana_cv = ?::numeric WHERE agencia_id = ? AND activo", total, husk, healthy, AGENCY);
    }

    private GreenCoffeePurchaseRequest green(BigDecimal pay) {
        return new GreenCoffeePurchaseRequest(rp, COMPLETE, null, null, 5, new BigDecimal("1000"), BigDecimal.ZERO,
                BigDecimal.ZERO, new BigDecimal("12000"), BigDecimal.ZERO, BigDecimal.ZERO, cash(pay));
    }

    private void grower(String idNumber, String address, String phone, boolean deceased) {
        jdbc.update("INSERT INTO terceros (identificacion, primer_nombre, segundo_nombre, primer_apellido, segundo_apellido,"
                + " agencia_id, direccion, telefono, tipo, fallecido, asociacion) VALUES (?, 'PRUEBA', 'SEIS', 'SERVIDOR', 'VALORES',"
                + " ?, ?, ?, 'C', ?, false)", idNumber, AGENCY, address, phone, deceased);
    }

    private static PurchasePaymentRequest cash(BigDecimal amount) {
        return amount == null ? null : new PurchasePaymentRequest(amount, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null);
    }

    private DryCoffeePurchaseRequest dryRequest(String idNumber, String address, String cellphone, BigDecimal pay) {
        return dryRequest(idNumber, address, cellphone, BigDecimal.ZERO, pay);
    }

    private DryCoffeePurchaseRequest dryRequest(String idNumber, String address, String cellphone, BigDecimal penalty,
            BigDecimal pay) {
        return dryRequest("RN", idNumber, address, cellphone, penalty, new BigDecimal("10"), pay);
    }

    private DryCoffeePurchaseRequest dryRequest(String special, String idNumber, String address, String cellphone,
            BigDecimal penalty, BigDecimal huskWeight, BigDecimal pay) {
        return new DryCoffeePurchaseRequest(rp, special, idNumber, address, cellphone, 5, new BigDecimal("1000"),
                BigDecimal.ZERO, new BigDecimal("200"), huskWeight,
                penalty, BigDecimal.ZERO, BigDecimal.ZERO, cash(pay));
    }

    private DryCoffeePurchaseRequest nespresso(String special, String huskWeight) {
        return dryRequest(special, COMPLETE, null, null, BigDecimal.ZERO, new BigDecimal(huskWeight), null);
    }

    private void inProgram(String program) {
        jdbc.update("INSERT INTO cupos_ness (cedula, nombres, programa, cupo) VALUES (?, 'PRUEBA', ?, '100000')",
                COMPLETE, program);
    }

    private DryCoffeePurchaseResponse saveDry(String idNumber, String address, String cellphone) {
        BigDecimal net = dry.preview(dryRequest(idNumber, address, cellphone, null)).netToPay();
        return dry.create(dryRequest(idNumber, address, cellphone, net));
    }

    private Map<String, Object> tercero(String idNumber) {
        return jdbc.queryForMap("SELECT id, direccion, telefono FROM terceros WHERE identificacion = ?", idNumber);
    }

    @Test
    void dryCoffeeTakesPricesFromTheAnnouncementAndTheSellerFromTerceros() {
        DryCoffeePurchaseResponse saved = saveDry(COMPLETE, "OTRA DIRECCION", "3999999999");
        AnnouncementResponse announcement = announcements.findLatest(AGENCY, rp, "RN");

        assertThat(saved.healthyUnitPrice()).isEqualByComparingTo(announcement.healthyUnitPrice());
        assertThat(saved.bonus()).isEqualByComparingTo(announcement.bonus());
        assertThat(saved.costs()).isEqualByComparingTo(announcement.costs());
        assertThat(saved.defectiveUnitPrice()).isEqualByComparingTo(announcement.defectiveUnitPrice());
        // Lo que ya esta en TERCEROS manda: lo digitado no lo cambia ni en la compra ni en el tercero.
        assertThat(saved.firstName()).isEqualTo("PRUEBA SEIS");
        assertThat(saved.lastName()).isEqualTo("SERVIDOR VALORES");
        assertThat(saved.growerType()).isEqualTo("C");
        assertThat(saved.address()).isEqualTo("VEREDA EL ALTO");
        assertThat(saved.cellphone()).isEqualTo("3001112233");
        assertThat(tercero(COMPLETE)).containsEntry("direccion", "VEREDA EL ALTO").containsEntry("telefono", "3001112233");
        assertThat(saved.cooperativeDiscount()).isPositive();
    }

    @Test
    void greenCoffeeTakesPriceBonusAndCostsFromTheCvAnnouncementLikeAccess() {
        GreenCoffeePurchaseRequest preview = new GreenCoffeePurchaseRequest(rp, COMPLETE, null, null, 5,
                new BigDecimal("1000"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("12000"),
                BigDecimal.ZERO, BigDecimal.ZERO, null);
        BigDecimal net = green.preview(preview).netToPay();
        GreenCoffeePurchaseResponse saved = green.create(new GreenCoffeePurchaseRequest(rp, COMPLETE, null, null, 5,
                new BigDecimal("1000"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("12000"),
                BigDecimal.ZERO, BigDecimal.ZERO, cash(net)));
        AnnouncementResponse announcement = announcements.findLatest(AGENCY, rp, "CV");

        // Q36: como Access (macro "Asignar numero anuncio verdes"), no del Registro de Control.
        assertThat(saved.healthyUnitPrice()).isEqualByComparingTo(announcement.healthyUnitPrice());
        assertThat(saved.bonus()).isEqualByComparingTo(announcement.bonus());
        assertThat(saved.costs()).isEqualByComparingTo(announcement.costs());
        assertThat(saved.firstName()).isEqualTo("PRUEBA SEIS");
        assertThat(saved.netToPay()).isEqualByComparingTo(net);
        // 6g3: encabezado de "Factura verdes" (punto de compra, prefijo y resolucion DIAN), como seco y pasilla.
        var controlRecord = controlRecords.getActive(AGENCY);
        assertThat(saved.prefix()).isEqualTo(controlRecord.getPrefix());
        assertThat(saved.purchasePoint()).isEqualTo(controlRecord.getPurchasePoint());
        assertThat(saved.dianResolution()).isEqualTo(controlRecord.getDianResolution());
        assertThat(green.findById(saved.id()).prefix()).isEqualTo(controlRecord.getPrefix());
    }

    @Test
    void huskTakesThePointPriceFromTheAnnouncement() {
        HuskPurchaseRequest preview = new HuskPurchaseRequest(rp, COMPLETE, null, null, new BigDecimal("200"), 5,
                new BigDecimal("1000"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null);
        BigDecimal net = husk.preview(preview).netToPay();
        HuskPurchaseResponse saved = husk.create(new HuskPurchaseRequest(rp, COMPLETE, null, null, new BigDecimal("200"), 5,
                new BigDecimal("1000"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, cash(net)));
        AnnouncementResponse announcement = announcements.findLatest(AGENCY, rp, "PASILLA");

        assertThat(saved.pointPrice()).isEqualByComparingTo(announcement.healthyUnitPrice());
        assertThat(saved.costs()).isEqualByComparingTo(announcement.costs());
        assertThat(saved.lastName()).isEqualTo("SERVIDOR VALORES");
    }

    @Test
    void aTypedContactCompletesTheEmptyOnesInTercerosWithItsHistory() {
        DryCoffeePurchaseResponse saved = saveDry(NO_CONTACT, " VEREDA NUEVA ", "3110000000");

        assertThat(saved.address()).isEqualTo("VEREDA NUEVA");
        assertThat(saved.cellphone()).isEqualTo("3110000000");
        Map<String, Object> tercero = tercero(NO_CONTACT);
        assertThat(tercero).containsEntry("direccion", "VEREDA NUEVA").containsEntry("telefono", "3110000000");
        Map<String, Object> history = jdbc.queryForMap("SELECT accion, antes::text AS antes, despues::text AS despues"
                + " FROM historial_cambios WHERE entidad = 'TERCERO' AND entidad_id = ?", tercero.get("id"));
        assertThat(history.get("accion")).isEqualTo("MODIFICAR");
        assertThat((String) history.get("despues")).contains("3110000000").contains("VEREDA NUEVA");
    }

    @Test
    void aPositivePenaltyIsSavedAsNegative() {
        BigDecimal net = dry.preview(dryRequest(COMPLETE, null, null, new BigDecimal("23"), null)).netToPay();
        DryCoffeePurchaseResponse saved = dry.create(dryRequest(COMPLETE, null, null, new BigDecimal("23"), net));

        assertThat(saved.penalty()).isEqualByComparingTo("-23");
        assertThat(saved.netToPay()).isEqualByComparingTo(net);
    }

    @Test
    void thePreviewNeverWritesTheTercero() {
        dry.preview(dryRequest(NO_CONTACT, "VEREDA NUEVA", "3110000000", null));

        assertThat(tercero(NO_CONTACT)).containsEntry("direccion", "").containsEntry("telefono", "");
    }

    @Test
    void anEmptyContactMustBeTyped() {
        assertThatThrownBy(() -> dry.preview(dryRequest(NO_CONTACT, "VEREDA NUEVA", " ", null)))
                .isInstanceOf(BusinessRuleException.class).hasMessage(PurchaseSellerServiceImpl.CELLPHONE_REQUIRED);
        assertThatThrownBy(() -> dry.preview(dryRequest(NO_CONTACT, null, "3110000000", null)))
                .isInstanceOf(BusinessRuleException.class).hasMessage(PurchaseSellerServiceImpl.ADDRESS_REQUIRED);
    }

    @Test
    void anUnknownOrDeceasedSellerIsRejectedWithoutTakingAnInvoiceNumber() {
        int next = consecutive.next(AGENCY, controlRecords.getActive(AGENCY)).invoiceNumber();
        BigDecimal anyAmount = BigDecimal.ONE;

        assertThatThrownBy(() -> dry.create(dryRequest("990000999", null, null, anyAmount)))
                .isInstanceOf(ResourceNotFoundException.class).hasMessage(PurchaseSellerServiceImpl.NOT_FOUND);
        assertThatThrownBy(() -> dry.create(dryRequest(DECEASED, null, null, anyAmount)))
                .isInstanceOf(BusinessRuleException.class).hasMessage(PurchaseSellerServiceImpl.DECEASED);
        assertThat(consecutive.next(AGENCY, controlRecords.getActive(AGENCY)).invoiceNumber()).isEqualTo(next);
    }

    @Test
    void theDailyWithholdingAddsDryGreenAndHuskOfEveryAgency() {
        // Q10: base 1.000.000 y 0,5 % para que se note con montos chicos.
        jdbc.update("UPDATE registros_control SET base_retencion = 1000000, porcentaje_retencion = 0.5"
                + " WHERE agencia_id = ? AND activo", AGENCY);
        GreenCoffeePurchaseRequest green600 = new GreenCoffeePurchaseRequest(rp, COMPLETE, null, null, 5,
                new BigDecimal("1000"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("600"),
                BigDecimal.ZERO, BigDecimal.ZERO, cash(new BigDecimal("595200")));
        GreenCoffeePurchaseResponse greenSaved = green.create(green600);
        assertThat(greenSaved.grossValue()).isEqualByComparingTo("600000");
        assertThat(greenSaved.withholding()).isEqualByComparingTo("0");
        // La compra de verde queda en otra agencia: igual cuenta para el mismo fiel.
        jdbc.update("UPDATE compras_cafe_verde SET agencia_id = (SELECT min(id) FROM agencias WHERE id <> ?) WHERE id = ?",
                AGENCY, greenSaved.id());

        var huskPreview = husk.preview(new HuskPurchaseRequest(rp, COMPLETE, null, null, new BigDecimal("200"), 5,
                new BigDecimal("1000"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null));

        BigDecimal expected = huskPreview.grossValue().add(new BigDecimal("600000"))
                .multiply(new BigDecimal("0.005")).setScale(0, RoundingMode.HALF_UP);
        assertThat(huskPreview.withholding()).isEqualByComparingTo(expected);
        assertThat(dailyTotals.sum(COMPLETE, LocalDate.now()).grossValue()).isEqualByComparingTo("600000");
        assertThat(dailyTotals.sum(COMPLETE, LocalDate.now().plusDays(1)).grossValue()).isEqualByComparingTo("0");
    }


    @Test
    void programCoffeesRequireTheSellerInItsNessProgramIncludingTheCupVariants() {
        String notInProgram = "CAFICULTOR NO PERTENECE A NINGUN PROGRAMA, NO LE PUEDE FACTURAR ESTE TIPO DE CAFE.";
        for (String special : new String[] {"NESPRESSO - FTUSA", "NESPRESSO CON TAZA - FTUSA",
                "NESPRESSO LATE HARVEST CON TAZA - FTUSA"}) {
            assertThatThrownBy(() -> dry.preview(nespresso(special, "10")))
                    .as(special).isInstanceOf(BusinessRuleException.class).hasMessage(notInProgram);
        }
        inProgram("NESPRESSO");
        inProgram("NESPRESSO LATE HARVEST");

        assertThat(dry.preview(nespresso("NESPRESSO - FTUSA", "10")).healthyPercentage()).isEqualByComparingTo("92.11");
        assertThat(dry.preview(nespresso("NESPRESSO CON TAZA - FTUSA", "10")).netToPay()).isPositive();
        assertThat(dry.preview(nespresso("NESPRESSO LATE HARVEST CON TAZA - FTUSA", "10")).netToPay()).isPositive();
    }

    @Test
    void aNespressoFactorOverTheThresholdIsRejectedButRnIsNot() {
        inProgram("NESPRESSO");
        // 200 / 14 -> Alm Sana 186 y Factor 250 x 70 / 186 = 94,09 > 93,33.
        assertThatThrownBy(() -> dry.preview(nespresso("NESPRESSO - FTUSA", "14")))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("UMBRAL_ESPECIALES");
        assertThatThrownBy(() -> dry.qualityPercentages(new BigDecimal("200"), new BigDecimal("14"), rp, "NESPRESSO - FTUSA"))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("UMBRAL_ESPECIALES");
        assertThat(dry.preview(dryRequest("RN", COMPLETE, null, null, BigDecimal.ZERO, new BigDecimal("14"), null))
                .healthyPercentage()).isEqualByComparingTo("94.09");
        assertThat(dry.qualityPercentages(new BigDecimal("200"), new BigDecimal("14"), null, null).healthyStoredWeight())
                .isEqualByComparingTo("186");
    }


    @Test
    void greenCoffeeShowsAndSavesTheFixedSampleOfTheControlRecord() {
        var info = green.announcementInfo();
        assertThat(info.sampleReady()).isTrue();
        assertThat(info.sample().wastePercentage()).isEqualByComparingTo("44.00");
        assertThat(info.sample().huskPercentage()).isEqualByComparingTo("5.00");
        assertThat(info.sample().factor()).isEqualByComparingTo("94.00");

        BigDecimal net = green.preview(green(null)).netToPay();
        GreenCoffeePurchaseResponse saved = green.create(green(net));

        assertThat(saved.sample().totalStoredWeight()).isEqualByComparingTo("140");
        assertThat(saved.sample().huskWeight()).isEqualByComparingTo("12.5");
        assertThat(saved.sample().healthyStoredWeight()).isEqualByComparingTo("186.18");
        assertThat(saved.sample().factor()).isEqualByComparingTo("94.00");
    }

    @Test
    void greenCoffeeIsBlockedWhileAGreenWeightIsZeroWithoutTakingAnInvoice() {
        greenSample("140", "0", "186.18");
        int next = consecutive.next(AGENCY, controlRecords.getActive(AGENCY)).invoiceNumber();

        assertThat(green.announcementInfo().sampleReady()).isFalse();
        assertThat(green.announcementInfo().sample()).isNull();
        assertThatThrownBy(() -> green.preview(green(null)))
                .isInstanceOf(BusinessRuleException.class).extracting("code").isEqualTo("VERDE_SIN_VALORES");
        assertThatThrownBy(() -> green.create(green(BigDecimal.ONE)))
                .isInstanceOf(BusinessRuleException.class).extracting("code").isEqualTo("VERDE_SIN_VALORES");
        assertThat(consecutive.next(AGENCY, controlRecords.getActive(AGENCY)).invoiceNumber()).isEqualTo(next);
    }

}
