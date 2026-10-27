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
import com.cafeoccidente.backend.purchases.shared.service.InvoiceConsecutiveService;
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
        for (String special : new String[] {"RN", "CV", "PASILLA"}) {
            Long id = jdbc.queryForObject("INSERT INTO anuncios (fecha_anuncio, pr_base_cps, pr_alm_defec, sobrepr_carga_esp,"
                    + " pr_inc_calidad, fondo_id, especial, activo) VALUES (CURRENT_DATE, 1546900, 6000, 80000, 50, ?, ?, true)"
                    + " RETURNING id", Long.class, rp, special);
            jdbc.update("INSERT INTO anuncios_agencias (agencia_id, anuncio_id, numero_anuncio, fecha_asignacion)"
                    + " SELECT ?, ?, COALESCE(MAX(numero_anuncio), 0) + 1, CURRENT_DATE FROM anuncios_agencias WHERE agencia_id = ?",
                    AGENCY, id, AGENCY);
        }
        grower(COMPLETE, "VEREDA EL ALTO", "3001112233", false);
        grower(NO_CONTACT, "", "", false);
        grower(DECEASED, "VEREDA BAJA", "3004445566", true);
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
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
        return new DryCoffeePurchaseRequest(rp, "RN", idNumber, address, cellphone, 5, new BigDecimal("1000"),
                BigDecimal.ZERO, new BigDecimal("200"), new BigDecimal("10"), new BigDecimal("190"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, cash(pay));
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
}
