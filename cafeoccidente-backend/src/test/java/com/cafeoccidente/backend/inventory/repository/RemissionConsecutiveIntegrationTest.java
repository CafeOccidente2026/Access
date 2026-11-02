package com.cafeoccidente.backend.inventory.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cafeoccidente.backend.common.security.Roles;
import com.cafeoccidente.backend.common.security.SecurityUser;
import com.cafeoccidente.backend.inventory.dto.RemissionLineRequest;
import com.cafeoccidente.backend.inventory.dto.RemissionRequest;
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
 * 7a (RN-11.4, RN-11.5): las Salidas de inventario y las remesas toman el numero PREFIJO-FONDO-CONSECUTIVO
 * del mismo consecutivo por agencia y fondo, asi nunca se repite un numero entre los dos procesos. Sobre la
 * semilla (agencia 1). Transaccional: no deja nada.
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
    private Long lf;
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
        lf = jdbc.queryForObject("SELECT id FROM fondos WHERE codigo = 'LF'", Long.class);
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

    private int last(Long agencyId, Long fundId) {
        List<Integer> last = jdbc.queryForList("SELECT ultimo_consecutivo FROM consecutivos_remision"
                + " WHERE agencia_id = ? AND fondo_id = ?", Integer.class, agencyId, fundId);
        return last.isEmpty() ? 0 : last.get(0);
    }

    @Test
    void aRemittanceTakesTheNumberThatFollowsTheLastInventoryExit() {
        Long movement = dryPurchaseInventory();

        String exit = remissions.create(new RemissionRequest(LocalDate.now(), "ALMACAFE", null,
                List.of(new RemissionLineRequest(movement, new BigDecimal("10.00"), 1, new BigDecimal("10.00")))))
                .displayNumber();
        int remittance = consecutives.take(AGENCY, rp);

        assertThat(exit).isEqualTo("SDBU-RP-%04d".formatted(remittance - 1));
        assertThat(last(AGENCY, rp)).isEqualTo(remittance);
    }

    @Test
    void eachAgencyAndFundHasItsOwnConsecutive() {
        Long other = jdbc.queryForObject("SELECT min(id) FROM agencias WHERE id <> ?", Long.class, AGENCY);
        int lfBefore = last(AGENCY, lf);
        int otherBefore = last(other, rp);

        int first = consecutives.take(AGENCY, rp);
        int second = consecutives.take(AGENCY, rp);

        assertThat(second).isEqualTo(first + 1);
        assertThat(last(AGENCY, lf)).isEqualTo(lfBefore);
        assertThat(last(other, rp)).isEqualTo(otherBefore);
        assertThat(consecutives.take(AGENCY, lf)).isEqualTo(lfBefore + 1);
        assertThat(consecutives.take(other, rp)).isEqualTo(otherBefore + 1);
    }

    @Test
    void theConsecutiveStartsAtTheHighestExitAlreadyNumbered() {
        Long behind = jdbc.queryForObject("""
                SELECT count(*) FROM (SELECT agencia_id, fondo_id, max(consecutivo) AS usado FROM remisiones
                                      WHERE fondo_id IS NOT NULL AND consecutivo IS NOT NULL GROUP BY 1, 2) u
                LEFT JOIN consecutivos_remision c ON c.agencia_id = u.agencia_id AND c.fondo_id = u.fondo_id
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
        assertThatThrownBy(() -> remittance(AGENCY, "SDBU-RP-9002", "BORRADA"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Long remittance(Long agencyId, String number, String status) {
        return jdbc.queryForObject("INSERT INTO remesas (agencia_id, fondo_id, numero_remesa, cantidad_empaques,"
                + " tipo_empaque, estado, creado_por_usuario_id, creado_en) VALUES (?, ?, ?, 10, 'USADO', ?, ?, now())"
                + " RETURNING id", Long.class, agencyId, rp, number, status, user.getId());
    }
}
