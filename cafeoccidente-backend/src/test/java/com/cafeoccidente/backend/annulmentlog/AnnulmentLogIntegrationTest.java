package com.cafeoccidente.backend.annulmentlog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cafeoccidente.backend.annulmentlog.dto.AnnulmentLogEntry;
import com.cafeoccidente.backend.annulmentlog.dto.AnnulmentLogFilter;
import com.cafeoccidente.backend.annulmentlog.service.AnnulmentLogService;
import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.common.security.Roles;
import com.cafeoccidente.backend.common.security.SecurityUser;
import com.cafeoccidente.backend.purchases.annulment.repository.AnnulmentModule;
import com.cafeoccidente.backend.purchases.annulment.service.PurchaseAnnulmentService;
import com.cafeoccidente.backend.purchases.drycoffee.dto.DryCoffeePurchaseRequest;
import com.cafeoccidente.backend.purchases.drycoffee.dto.DryCoffeePurchaseResponse;
import com.cafeoccidente.backend.purchases.drycoffee.service.DryCoffeePurchaseService;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registro de anulaciones (26-nov, aval del area): cada anulacion deja una fila con quien, cuando, por que y los
 * valores del documento antes de quedar en 0. La tabla solo admite insertar. Sobre la semilla (agencia 1), asi corre
 * en CI. Transaccional: no deja nada.
 */
@SpringBootTest
@Transactional
@SuppressWarnings("null")
class AnnulmentLogIntegrationTest {

    private static final String SELLER = "990000998";
    private static final long AGENCY = 1L;
    private static final String REASON = "Error de digitación en los kilos";

    @Autowired
    private DryCoffeePurchaseService dry;
    @Autowired
    private PurchaseAnnulmentService annulment;
    @Autowired
    private AnnulmentLogService log;
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
    void pointWithAnAnnouncementAndASeller() {
        User user = new User();
        user.setUsername("prueba_anulaciones");
        user.setPasswordHash("x");
        user.setRole(roles.findByName(Roles.USUARIO).orElseThrow());
        user.setAgency(agencies.findById(AGENCY).orElseThrow());
        SecurityUser principal = new SecurityUser(users.save(user));
        userId = principal.getUserId();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        rp = jdbc.queryForObject("SELECT id FROM fondos WHERE codigo = 'RP'", Long.class);
        Long id = jdbc.queryForObject("INSERT INTO anuncios (fecha_anuncio, pr_base_cps, pr_alm_defec, sobrepr_carga_esp,"
                + " pr_inc_calidad, fondo_id, especial, activo) VALUES (CURRENT_DATE, 1546900, 6000, 80000, 50, ?, 'RN',"
                + " true) RETURNING id", Long.class, rp);
        jdbc.update("INSERT INTO anuncios_agencias (agencia_id, anuncio_id, numero_anuncio, fecha_asignacion)"
                + " SELECT ?, ?, COALESCE(MAX(numero_anuncio), 0) + 1, CURRENT_DATE FROM anuncios_agencias WHERE agencia_id = ?",
                AGENCY, id, AGENCY);
        jdbc.update("INSERT INTO terceros (identificacion, primer_nombre, primer_apellido, agencia_id, direccion, telefono, tipo,"
                + " asociacion) VALUES (?, 'PRUEBA', 'ANULACION', ?, 'VEREDA', '3001112233', 'C', false)", SELLER, AGENCY);
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    private DryCoffeePurchaseResponse purchase() {
        DryCoffeePurchaseRequest request = request(null);
        BigDecimal net = dry.preview(request).netToPay();
        return dry.create(request(new PurchasePaymentRequest(net, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null)));
    }

    private DryCoffeePurchaseRequest request(PurchasePaymentRequest payment) {
        return new DryCoffeePurchaseRequest(rp, "RN", SELLER, null, null, 5, new BigDecimal("1000"), BigDecimal.ZERO,
                new BigDecimal("200"), new BigDecimal("10"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, payment);
    }

    private AnnulmentLogEntry entryOf(String type, Long documentId) {
        return log.search(new AnnulmentLogFilter(AGENCY, type, null, null, null, true, 0, 200)).items().stream()
                .filter(e -> documentId.equals(e.documentId())).findFirst().orElseThrow();
    }

    @Test
    void annullingAPurchaseRecordsWhoWhenWhyAndTheValuesBeforeTheZeros() {
        DryCoffeePurchaseResponse saved = purchase();
        Map<String, Object> before = jdbc.queryForMap(
                "SELECT kilos_brutos, kilos_netos, vr_bruto, neto_a_pagar FROM compras_cafe_seco WHERE id = ?", saved.id());
        String prefix = jdbc.queryForObject("SELECT prefijo FROM registros_control WHERE agencia_id = ? AND activo",
                String.class, AGENCY);

        annulment.annul(AGENCY, AnnulmentModule.DRY, saved.id(), "  " + REASON + "  ");

        AnnulmentLogEntry entry = entryOf("COMPRA_SECO", saved.id());
        assertThat(entry.origin()).isEqualTo("WEB");
        assertThat(entry.documentNumber()).isEqualTo(prefix + " " + saved.invoiceNumber());
        assertThat(entry.username()).isEqualTo("prueba_anulaciones");
        assertThat(entry.reason()).isEqualTo(REASON);
        assertThat(entry.annulledAt()).isNotNull();
        assertThat(new BigDecimal(entry.values().get("netKg"))).isEqualByComparingTo((BigDecimal) before.get("kilos_netos"));
        assertThat(new BigDecimal(entry.values().get("grossKg"))).isEqualByComparingTo((BigDecimal) before.get("kilos_brutos"));
        assertThat(new BigDecimal(entry.values().get("grossValue"))).isEqualByComparingTo((BigDecimal) before.get("vr_bruto"));
        assertThat(new BigDecimal(entry.values().get("netToPay"))).isEqualByComparingTo((BigDecimal) before.get("neto_a_pagar"));
        assertThat(entry.values()).containsEntry("paymentMethod", "EFECTIVO").containsEntry("idNumber", SELLER)
                .containsEntry("sellerName", "PRUEBA ANULACION");
        assertThat(jdbc.queryForObject("SELECT estado FROM compras_cafe_seco WHERE id = ?", String.class, saved.id()))
                .isEqualTo("ANULADA");
    }

    @Test
    void theAnnulledInvoiceKeepsItsNumberAndTheNumberIsNeverReused() {
        DryCoffeePurchaseResponse first = purchase();
        annulment.annul(AGENCY, AnnulmentModule.DRY, first.id(), REASON);

        DryCoffeePurchaseResponse next = purchase();

        assertThat(jdbc.queryForObject("SELECT numero_factura FROM compras_cafe_seco WHERE id = ?", Integer.class,
                first.id())).isEqualTo(first.invoiceNumber());
        assertThat(next.invoiceNumber()).isGreaterThan(first.invoiceNumber());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM compras_cafe_seco WHERE agencia_id = ? AND numero_factura = ?",
                Integer.class, AGENCY, first.invoiceNumber())).isOne();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "corto", "  123456789  "})
    void aReasonShorterThanTenCharactersIsRejectedAndNothingChanges(String reason) {
        DryCoffeePurchaseResponse saved = purchase();

        assertThatThrownBy(() -> annulment.annul(AGENCY, AnnulmentModule.DRY, saved.id(), reason))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("El motivo de la anulación es obligatorio (mínimo 10 caracteres)");
        assertThat(jdbc.queryForObject("SELECT estado FROM compras_cafe_seco WHERE id = ?", String.class, saved.id()))
                .isEqualTo("VALIDA");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM anulaciones WHERE documento_id = ?", Integer.class,
                saved.id())).isZero();
    }

    /**
     * Un caso por sentencia: en PostgreSQL, despues de un error la transaccion queda abortada. Dos capas: si la app
     * entra con su propio rol (CI, produccion), afterMigrate.sql ya le quito el permiso y la base responde "permiso
     * denegado"; si entra con el rol dueno (instalaciones de antes), el trigger lo rechaza. Las dos sirven.
     */
    @ParameterizedTest
    @ValueSource(strings = {"UPDATE anulaciones SET motivo = 'cambiado por alguien' WHERE id = %d",
        "DELETE FROM anulaciones WHERE id = %d", "TRUNCATE anulaciones"})
    void theTableOnlyAcceptsInserts(String statement) {
        Long id = accessRow();

        assertThatThrownBy(() -> jdbc.update(statement.formatted(id))).isInstanceOf(DataAccessException.class)
                .rootCause().hasMessageMatching("(?s).*(solo admite insertar|permission denied|permiso denegado).*");
    }

    /** El trigger protege tambien al dueno de la tabla (y a cualquier rol que tuviera el permiso). */
    @Test
    void theTriggersThatRejectChangesExist() {
        assertThat(jdbc.queryForList("SELECT tgname FROM pg_trigger WHERE tgrelid = 'anulaciones'::regclass"
                + " AND NOT tgisinternal ORDER BY tgname", String.class))
                .containsExactly("tg_anulaciones_sin_cambios", "tg_anulaciones_sin_truncate");
    }

    @Test
    void anAccessRowHasNoUserDateNorReasonButAWebRowNeedsThem() {
        Long id = accessRow();
        assertThat(jdbc.queryForObject("SELECT usuario_id IS NULL AND anulado_en IS NULL AND motivo IS NULL"
                + " FROM anulaciones WHERE id = ?", Boolean.class, id)).isTrue();

        assertThatThrownBy(() -> jdbc.update("INSERT INTO anulaciones (origen, tipo_documento, numero_documento, agencia_id)"
                + " VALUES ('WEB', 'COMPRA_SECO', 'XX 1', ?)", AGENCY)).isInstanceOf(DataIntegrityViolationException.class);
    }

    /**
     * 26-nov: las de Access no tienen fecha de anulacion. Con la casilla marcada (por defecto) salen aunque haya
     * Desde/Hasta, y traen la fecha de la compra; desmarcada, no salen.
     */
    @Test
    void accessRowsIgnoreTheDatesWhenIncludedAndShowThePurchaseDate() {
        DryCoffeePurchaseResponse saved = purchase();
        LocalDate bought = jdbc.queryForObject("SELECT fecha_compra FROM compras_cafe_seco WHERE id = ?", LocalDate.class,
                saved.id());
        Long id = jdbc.queryForObject("INSERT INTO anulaciones (origen, tipo_documento, numero_documento, agencia_id,"
                + " documento_id, nota) VALUES ('ACCESS', 'COMPRA_SECO', 'XX 999002', ?, ?, 'Anulada en Access')"
                + " RETURNING id", Long.class, AGENCY, saved.id());
        LocalDate day = LocalDate.of(2000, 1, 1);

        List<AnnulmentLogEntry> included = log.search(new AnnulmentLogFilter(AGENCY, null, null, day, day, true, 0, 200))
                .items();
        List<AnnulmentLogEntry> excluded = log.search(new AnnulmentLogFilter(AGENCY, null, null, null, null, false, 0, 200))
                .items();

        assertThat(included).filteredOn(e -> id.equals(e.id())).singleElement()
                .satisfies(e -> assertThat(e.purchaseDate()).isEqualTo(bought));
        assertThat(included).allMatch(e -> "ACCESS".equals(e.origin()));
        assertThat(excluded).noneMatch(e -> "ACCESS".equals(e.origin()));
    }

    @Test
    void aWebRowIsFilteredByItsAnnulmentDayAndAlsoShowsThePurchaseDate() {
        DryCoffeePurchaseResponse saved = purchase();
        annulment.annul(AGENCY, AnnulmentModule.DRY, saved.id(), REASON);
        LocalDate today = jdbc.queryForObject("SELECT CAST(now() AT TIME ZONE 'America/Bogota' AS DATE)", LocalDate.class);

        assertThat(entryOf("COMPRA_SECO", saved.id()).purchaseDate()).isEqualTo(jdbc.queryForObject(
                "SELECT fecha_compra FROM compras_cafe_seco WHERE id = ?", LocalDate.class, saved.id()));
        assertThat(log.search(new AnnulmentLogFilter(AGENCY, null, null, today, today, false, 0, 200)).items())
                .anyMatch(e -> saved.id().equals(e.documentId()));
        assertThat(log.search(new AnnulmentLogFilter(AGENCY, null, null, today.minusDays(2), today.minusDays(1), false, 0,
                200)).items()).noneMatch(e -> saved.id().equals(e.documentId()));
    }

    private Long accessRow() {
        return jdbc.queryForObject("INSERT INTO anulaciones (origen, tipo_documento, numero_documento, agencia_id, valores,"
                + " nota) VALUES ('ACCESS', 'COMPRA_SECO', 'XX 999001', ?, '{\"netKg\": \"0\"}',"
                + " 'Access deja la compra en 0 al anular') RETURNING id", Long.class, AGENCY);
    }
}
