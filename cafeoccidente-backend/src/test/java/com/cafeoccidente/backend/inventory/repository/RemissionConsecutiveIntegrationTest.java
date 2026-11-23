package com.cafeoccidente.backend.inventory.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.common.security.Roles;
import com.cafeoccidente.backend.common.security.SecurityUser;
import com.cafeoccidente.backend.inventory.dto.RemissionLineRequest;
import com.cafeoccidente.backend.inventory.dto.RemissionRequest;
import com.cafeoccidente.backend.inventory.dto.InventoryMovementResponse;
import com.cafeoccidente.backend.inventory.service.InventoryMovementService;
import com.cafeoccidente.backend.inventory.service.RemissionService;
import com.cafeoccidente.backend.purchases.drycoffee.dto.DryCoffeePurchaseRequest;
import com.cafeoccidente.backend.purchases.drycoffee.service.DryCoffeePurchaseService;
import com.cafeoccidente.backend.purchases.shared.dto.PurchasePaymentRequest;
import com.cafeoccidente.backend.purchases.shared.repository.AgencyRepository;
import com.cafeoccidente.backend.users.entity.User;
import com.cafeoccidente.backend.users.repository.RoleRepository;
import com.cafeoccidente.backend.users.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Demo del 23-nov (V31): la remision y su remesa tienen el mismo numero PREFIJO-CONSECUTIVO, de un solo consecutivo
 * por agencia (sin fondo). Sobre la semilla (agencia 1). Transaccional: no deja nada.
 */
@SpringBootTest
@Transactional
@SuppressWarnings("null")
class RemissionConsecutiveIntegrationTest {

    private static final String SELLER = "990007001";
    private static final long AGENCY = 1L;

    @Autowired
    private RemissionConsecutiveRepository consecutives;
    @Autowired
    private RemissionService remissions;
    @Autowired
    private InventoryMovementService movements;
    @Autowired
    private DryCoffeePurchaseService dry;
    @Autowired
    private UserRepository users;
    @Autowired
    private RoleRepository roles;
    @Autowired
    private AgencyRepository agencies;
    @Autowired
    private JdbcTemplate jdbc;

    private Long rp;
    private User user;

    @BeforeEach
    void agencyWithAnRnAnnouncementAndASeller() {
        user = new User();
        user.setUsername("prueba_7a");
        user.setPasswordHash("x");
        user.setRole(roles.findByName(Roles.USUARIO).orElseThrow());
        user.setAgency(agencies.findById(AGENCY).orElseThrow());
        SecurityUser principal = new SecurityUser(users.save(user));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        rp = jdbc.queryForObject("SELECT id FROM fondos WHERE codigo = 'RP'", Long.class);
        Long announcement = jdbc.queryForObject("INSERT INTO anuncios (fecha_anuncio, pr_base_cps, pr_alm_defec,"
                + " sobrepr_carga_esp, pr_inc_calidad, fondo_id, especial, activo) VALUES (CURRENT_DATE, 1546900, 6000,"
                + " 80000, 50, ?, 'RN', true) RETURNING id", Long.class, rp);
        jdbc.update("INSERT INTO anuncios_agencias (agencia_id, anuncio_id, numero_anuncio, fecha_asignacion)"
                + " SELECT ?, ?, COALESCE(MAX(numero_anuncio), 0) + 1, CURRENT_DATE FROM anuncios_agencias WHERE agencia_id = ?",
                AGENCY, announcement, AGENCY);
        jdbc.update("INSERT INTO terceros (identificacion, primer_nombre, primer_apellido, agencia_id, direccion, telefono, tipo,"
                + " asociacion) VALUES (?, 'PRUEBA', 'REMESA', ?, 'VEREDA', '3001112233', 'C', false)", SELLER, AGENCY);
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    /** Entrada de inventario de una compra de seco RN (fondo RP) recien guardada. */
    private Long dryPurchaseInventory() {
        DryCoffeePurchaseRequest request = request(null);
        BigDecimal net = dry.preview(request).netToPay();
        Long purchaseId = dry.create(request(new PurchasePaymentRequest(net, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, null))).id();
        return jdbc.queryForObject("SELECT id FROM inventarios WHERE modulo_compra = 'DRY_COFFEE' AND compra_id = ?",
                Long.class, purchaseId);
    }

    private DryCoffeePurchaseRequest request(PurchasePaymentRequest payment) {
        return new DryCoffeePurchaseRequest(rp, "RN", SELLER, null, null, 5, new BigDecimal("1000"), BigDecimal.ZERO,
                new BigDecimal("200"), new BigDecimal("10"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, payment);
    }

    private int last(Long agencyId) {
        List<Integer> last = jdbc.queryForList("SELECT ultimo_consecutivo FROM consecutivos_remision_agencia"
                + " WHERE agencia_id = ?", Integer.class, agencyId);
        return last.isEmpty() ? 0 : last.get(0);
    }

    @Test
    void theRemissionTakesPrefixAndTheNextConsecutiveOfTheAgency() {
        Long movement = dryPurchaseInventory();
        int before = last(AGENCY);

        String exit = remissions.create(new RemissionRequest(LocalDate.now(), "ALMACAFE", null,
                List.of(new RemissionLineRequest(movement)), 5, "USADO")).displayNumber();

        assertThat(exit).isEqualTo("SDBU-" + (before + 1));
        assertThat(last(AGENCY)).isEqualTo(before + 1);
    }

    /** Respuestas del area (23-nov): la remision genera su remesa con el mismo numero, marca la compra, saca los
     *  empaques y la factura ya no esta disponible; al anularla todo vuelve y queda en el Historial. */
    @Test
    void savingARemissionGeneratesItsRemittanceAndAnnullingItUndoesEverything() {
        Long movement = dryPurchaseInventory();
        Long purchase = jdbc.queryForObject("SELECT compra_id FROM inventarios WHERE id = ?", Long.class, movement);
        BigDecimal balance = jdbc.queryForObject("SELECT saldo_kilos FROM inventarios WHERE id = ?", BigDecimal.class, movement);

        var remission = remissions.create(new RemissionRequest(LocalDate.now(), "ALMACAFE", null,
                List.of(new RemissionLineRequest(movement)), 5, "nuevo"));

        assertThat(remission.lines().get(0).quantity()).isEqualByComparingTo(balance);
        assertThat(jdbc.queryForObject("SELECT numero_remesa FROM remesas WHERE id = ? AND remision_id = ?",
                String.class, remission.remittanceId(), remission.id())).isEqualTo(remission.displayNumber());
        assertThat(jdbc.queryForObject("SELECT remesa_id FROM compras_cafe_seco WHERE id = ?", Long.class, purchase))
                .isEqualTo(remission.remittanceId());
        assertThat(jdbc.queryForObject("SELECT salidas FROM empaques WHERE agencia_id = ? AND id_transaccion = ?"
                + " AND tipo_empaque = 'NUEVO'", Integer.class, AGENCY, remission.displayNumber())).isEqualTo(5);
        InventoryMovementResponse row = movements.listByAgency(AGENCY).stream()
                .filter(m -> m.id().equals(movement)).findFirst().orElseThrow();
        assertThat(row.remitted()).isTrue();
        assertThat(row.remainingKg()).isZero();
        RemissionRequest again = new RemissionRequest(LocalDate.now(), "ALMACAFE", null,
                List.of(new RemissionLineRequest(movement)), 5, "USADO");
        assertThatThrownBy(() -> remissions.create(again)).isInstanceOf(BusinessRuleException.class);

        var annulled = remissions.annul(remission.id());

        assertThat(annulled.status()).isEqualTo("ANULADA");
        assertThat(jdbc.queryForObject("SELECT remesa_id FROM compras_cafe_seco WHERE id = ?", Long.class, purchase)).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM empaques WHERE agencia_id = ? AND id_transaccion = ?",
                Integer.class, AGENCY, remission.displayNumber())).isZero();
        assertThat(movements.findById(movement).remainingKg()).isEqualByComparingTo(balance);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM historial_cambios WHERE entidad = 'REMISION'"
                + " AND entidad_id = ? AND accion = 'ANULAR'", Integer.class, remission.id())).isEqualTo(1);
        assertThat(movements.findById(movement).remitted()).isFalse();
        assertThatThrownBy(() -> remissions.annul(remission.id())).isInstanceOf(BusinessRuleException.class);
    }

    /** C7 (prueba del 11-nov, punto 7): la lista de Registrar Salidas muestra la cedula y filtra por el fondo. */
    @Test
    void theInventoryListBringsTheSellerAndTheFundOfThePurchase() {
        Long movement = dryPurchaseInventory();

        InventoryMovementResponse row = movements.listByAgency(AGENCY).stream()
                .filter(m -> m.id().equals(movement)).findFirst().orElseThrow();

        assertThat(row.idNumber()).isEqualTo(SELLER);
        assertThat(row.fundCode()).isEqualTo("RP");
        assertThat(movements.findById(movement).idNumber()).isEqualTo(SELLER);
    }

    @Test
    void eachAgencyHasItsOwnConsecutiveForBothFunds() {
        Long other = jdbc.queryForObject("SELECT min(id) FROM agencias WHERE id <> ?", Long.class, AGENCY);
        int otherBefore = last(other);

        int first = consecutives.take(AGENCY);
        int second = consecutives.take(AGENCY);

        assertThat(second).isEqualTo(first + 1);
        assertThat(last(other)).isEqualTo(otherBefore);
        assertThat(consecutives.take(other)).isEqualTo(otherBefore + 1);
    }

    @Test
    void theConsecutiveStartsAtTheHighestNumberAlreadyUsed() {
        Long behind = jdbc.queryForObject("""
                SELECT count(*) FROM (SELECT agencia_id, max(consecutivo) AS usado FROM remisiones
                                      WHERE consecutivo IS NOT NULL GROUP BY 1
                                      UNION ALL SELECT agencia_id, max(consecutivo) FROM remesas
                                      WHERE consecutivo IS NOT NULL GROUP BY 1) u
                LEFT JOIN consecutivos_remision_agencia c ON c.agencia_id = u.agencia_id
                WHERE c.ultimo_consecutivo IS NULL OR c.ultimo_consecutivo < u.usado""", Long.class);
        assertThat(behind).isZero();
    }

    @Test
    void aRemittanceNumberIsUniquePerAgencyAndThePurchasesPointToIt() {
        Long other = jdbc.queryForObject("SELECT min(id) FROM agencias WHERE id <> ?", Long.class, AGENCY);
        Long remittance = remittance(AGENCY, "SDBU-RP-9001", "VIGENTE");
        Long purchase = jdbc.queryForObject("SELECT compra_id FROM inventarios WHERE id = ?", Long.class,
                dryPurchaseInventory());

        jdbc.update("UPDATE compras_cafe_seco SET remesa_id = ? WHERE id = ?", remittance, purchase);

        assertThat(jdbc.queryForObject("SELECT remesa_id FROM compras_cafe_seco WHERE id = ?", Long.class, purchase))
                .isEqualTo(remittance);
        assertThat(remittance(other, "SDBU-RP-9001", "VIGENTE")).isNotNull();
        assertThatThrownBy(() -> remittance(AGENCY, "SDBU-RP-9001", "VIGENTE")).isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void aRemittanceIsEitherInForceOrUnmarked() {
        assertThat(remittance(AGENCY, "SDBU-RP-9003", "DESMARCADA")).isNotNull();
        assertThatThrownBy(() -> remittance(AGENCY, "SDBU-RP-9002", "BORRADA"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Long remittance(Long agencyId, String number, String status) {
        return jdbc.queryForObject("INSERT INTO remesas (agencia_id, fondo_id, numero_remesa, cantidad_empaques,"
                + " tipo_empaque, estado, creado_por_usuario_id, creado_en) VALUES (?, ?, ?, 10, 'USADO', ?, ?, now())"
                + " RETURNING id", Long.class, agencyId, rp, number, status, user.getId());
    }
}
