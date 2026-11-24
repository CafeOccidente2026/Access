package com.cafeoccidente.backend.purchases.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cafeoccidente.backend.common.security.JwtService;
import com.cafeoccidente.backend.common.security.Roles;
import com.cafeoccidente.backend.common.security.SecurityUser;
import com.cafeoccidente.backend.purchases.drycoffee.dto.DryCoffeePurchaseRequest;
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
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 6g3 (seguridad): GET /{id} de seco, verde, pasilla y compras a futuro solo da compras de la agencia de
 * trabajo; la de otra agencia responde 404, como Consultar Compras. Revision del 24-nov (todos los endpoints que
 * reciben un id): inventario, remision, correccion, Registro de Control, Historial, cupos y compra a futuro en la
 * compra de seco, siempre por HTTP con un Usuario de otra agencia. Sobre la semilla (agencia 1). Transaccional.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@SuppressWarnings("null")
class PurchaseByIdAgencyIntegrationTest {

    private static final String SELLER = "990001111";
    private static final long AGENCY = 1L;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
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

    private User user(String username, Long agencyId) {
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash("x");
        user.setRole(roles.findByName(Roles.USUARIO).orElseThrow());
        user.setAgency(agencies.findById(agencyId).orElseThrow());
        return users.save(user);
    }

    private String token(User user) {
        return "Bearer " + jwtService.generateToken(new SecurityUser(user));
    }

    @BeforeEach
    void purchasesOfAgencyOne() {
        SecurityUser principal = new SecurityUser(user("prueba_6g3_id", AGENCY));
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
                + " asociacion) VALUES (?, 'PRUEBA', 'AGENCIA', ?, 'VEREDA', '3001112233', 'C', false)", SELLER, AGENCY);
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    private static PurchasePaymentRequest cash(BigDecimal net) {
        return new PurchasePaymentRequest(net, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null);
    }

    /** Una compra de cada modulo en la agencia 1: url de su GET /{id}. */
    private Map<String, Long> purchases() {
        DryCoffeePurchaseRequest d = new DryCoffeePurchaseRequest(rp, "RN", SELLER, null, null, 5, new BigDecimal("1000"),
                BigDecimal.ZERO, new BigDecimal("200"), new BigDecimal("10"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null);
        GreenCoffeePurchaseRequest g = new GreenCoffeePurchaseRequest(rp, SELLER, null, null, 5, new BigDecimal("1000"),
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("12000"), BigDecimal.ZERO, BigDecimal.ZERO, null);
        HuskPurchaseRequest h = new HuskPurchaseRequest(rp, SELLER, null, null, new BigDecimal("200"), 5, new BigDecimal("1000"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null);
        return Map.of(
                "/api/purchases/dry-coffee/", dry.create(new DryCoffeePurchaseRequest(rp, "RN", SELLER, null, null, 5,
                        new BigDecimal("1000"), BigDecimal.ZERO, new BigDecimal("200"), new BigDecimal("10"), BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO, cash(dry.preview(d).netToPay()))).id(),
                "/api/purchases/green-coffee/", green.create(new GreenCoffeePurchaseRequest(rp, SELLER, null, null, 5,
                        new BigDecimal("1000"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("12000"), BigDecimal.ZERO,
                        BigDecimal.ZERO, cash(green.preview(g).netToPay()))).id(),
                "/api/purchases/husk/", husk.create(new HuskPurchaseRequest(rp, SELLER, null, null, new BigDecimal("200"), 5,
                        new BigDecimal("1000"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, cash(husk.preview(h).netToPay()))).id());
    }

    @Test
    void aUserOfAnotherAgencyGets404AndOneOfTheSameAgencyGetsThePurchase() throws Exception {
        Map<String, Long> urls = purchases();
        Long other = jdbc.queryForObject("SELECT min(id) FROM agencias WHERE id <> ? AND activa", Long.class, AGENCY);
        String sameAgency = token(user("prueba_6g3_misma", AGENCY));
        String otherAgency = token(user("prueba_6g3_otra", other));

        for (Map.Entry<String, Long> purchase : urls.entrySet()) {
            String url = purchase.getKey() + purchase.getValue();
            mockMvc.perform(get(url).header("Authorization", sameAgency)).andExpect(status().isOk());
            mockMvc.perform(get(url).header("Authorization", otherAgency)).andExpect(status().isNotFound());
        }
    }

    @Test
    void aFuturePurchaseOfAnotherAgencyGives404() throws Exception {
        User owner = user("prueba_6g3_futuro", AGENCY);
        Long id = jdbc.queryForObject("INSERT INTO compras_anunciadas (creado_en, agencia_id, numero_anuncio, fecha_anuncio,"
                + " cedula, nombres, tipo, direccion, especial, kilos, saldo, fecha_entrega, pr_alm_sana, pr_alm_defec,"
                + " bonificacion, costos, pr_inc_calidad, pr_base_cps, creado_por_usuario_id) VALUES (now(), ?, 1, CURRENT_DATE,"
                + " ?, 'PRUEBA', 'C', 'VEREDA', 'RN', 100, 100, CURRENT_DATE, 0, 0, 0, 0, 0, 0, ?) RETURNING id",
                Long.class, AGENCY, SELLER, owner.getId());
        Long other = jdbc.queryForObject("SELECT min(id) FROM agencias WHERE id <> ? AND activa", Long.class, AGENCY);
        String url = "/api/purchases/future/" + id;

        mockMvc.perform(get(url).header("Authorization", token(owner))).andExpect(status().isOk());
        mockMvc.perform(get(url).header("Authorization", token(user("prueba_6g3_futuro_otra", other))))
                .andExpect(status().isNotFound());
    }

    private Long otherAgency() {
        return jdbc.queryForObject("SELECT min(id) FROM agencias WHERE id <> ? AND activa", Long.class, AGENCY);
    }

    /** Un Usuario de otra agencia con una pantalla concedida (RF-02). */
    private String otherWithScreen(String username, String screen) {
        User other = user(username, otherAgency());
        jdbc.update("INSERT INTO permisos (usuario_id, pantalla, concedido_por, concedido_en) VALUES (?, ?, ?, now())",
                other.getId(), screen, other.getId());
        return token(other);
    }

    private Long dryPurchase() {
        DryCoffeePurchaseRequest d = new DryCoffeePurchaseRequest(rp, "RN", SELLER, null, null, 5, new BigDecimal("1000"),
                BigDecimal.ZERO, new BigDecimal("200"), new BigDecimal("10"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null);
        return dry.create(new DryCoffeePurchaseRequest(rp, "RN", SELLER, null, null, 5, new BigDecimal("1000"),
                BigDecimal.ZERO, new BigDecimal("200"), new BigDecimal("10"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                cash(dry.preview(d).netToPay()))).id();
    }

    @Test
    void inventoryMovementAndRemissionOfAnotherAgencyGive404() throws Exception {
        Long movement = jdbc.queryForObject("SELECT id FROM inventarios WHERE modulo_compra = 'DRY_COFFEE' AND compra_id = ?",
                Long.class, dryPurchase());
        String sameAgency = token(user("prueba_idor_misma", AGENCY));
        String other = token(user("prueba_idor_otra", otherAgency()));
        String remissionBody = "{\"remissionDate\":\"" + LocalDate.now() + "\",\"destination\":\"PRUEBA\","
                + "\"lines\":[{\"inventoryMovementId\":" + movement + "}],\"packagingQuantity\":5,\"packagingType\":\"USADO\"}";

        // Hallazgo 1: el movimiento de inventario.
        mockMvc.perform(get("/api/inventory-movements/" + movement).header("Authorization", sameAgency))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/inventory-movements/" + movement).header("Authorization", other))
                .andExpect(status().isNotFound());
        // Una remision de otra agencia no puede sacar ese movimiento.
        mockMvc.perform(post("/api/remissions").header("Authorization", other).contentType(MediaType.APPLICATION_JSON)
                .content(remissionBody)).andExpect(status().isNotFound());

        // Hallazgo 2: la remision.
        mockMvc.perform(post("/api/remissions").header("Authorization", sameAgency).contentType(MediaType.APPLICATION_JSON)
                .content(remissionBody)).andExpect(status().is2xxSuccessful());
        Long remission = jdbc.queryForObject("SELECT remision_id FROM remisiones_detalle WHERE inventario_id = ?",
                Long.class, movement);
        mockMvc.perform(get("/api/remissions/" + remission).header("Authorization", sameAgency))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/remissions/" + remission).header("Authorization", other))
                .andExpect(status().isNotFound());
    }

    @Test
    void aCorrectionOfAnotherAgencyGives404EvenWithThePermission() throws Exception {
        Long purchase = dryPurchase();
        String other = otherWithScreen("prueba_idor_corrige", "CORREGIR_COMPRA");
        String body = "{\"fundId\":" + rp + ",\"specialType\":\"RN\",\"idNumber\":\"" + SELLER + "\",\"bagsCount\":5,"
                + "\"grossKg\":1000,\"tareKg\":0,\"totalStoredWeight\":200,\"defectiveStoredWeight\":10,\"penalty\":0,"
                + "\"freightDiscount\":0,\"otherDiscounts\":0}";

        mockMvc.perform(post("/api/purchases/correction/DRY/" + purchase + "/preview").header("Authorization", other)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isNotFound());
    }

    @Test
    void theControlRecordOfAnotherAgencyIsForbiddenEvenWithThePermission() throws Exception {
        String other = otherWithScreen("prueba_idor_registro", "REGISTRO_CONTROL");

        mockMvc.perform(get("/api/control-records/" + AGENCY).header("Authorization", other))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/control-records/" + AGENCY).header("Authorization", other)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
    }

    @Test
    void historyAndNessBalancesIgnoreTheAgencyAskedByAUser() throws Exception {
        String other = otherWithScreen("prueba_idor_historial", "HISTORIAL");

        String own = mockMvc.perform(get("/api/history").header("Authorization", other))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String asked = mockMvc.perform(get("/api/history?agencyId=" + AGENCY).header("Authorization", other))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(asked).isEqualTo(own);

        String balances = mockMvc.perform(get("/api/vendors/ness-quota-balances").header("Authorization", other))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String balancesAsked = mockMvc.perform(get("/api/vendors/ness-quota-balances?agencyId=" + AGENCY)
                .header("Authorization", other)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(balancesAsked).isEqualTo(balances);
    }

    @Test
    void quotasAndFuturePurchasesOfAnotherAgencyAreNotFound() throws Exception {
        Long announcement = jdbc.queryForObject("SELECT anuncio_id FROM anuncios_agencias WHERE agencia_id = ? LIMIT 1",
                Long.class, AGENCY);
        jdbc.update("INSERT INTO anuncios_agencias (agencia_id, anuncio_id, numero_anuncio, fecha_asignacion)"
                + " VALUES (?, ?, 987654, CURRENT_DATE)", AGENCY, announcement);
        User owner = user("prueba_idor_futuro", AGENCY);
        Long future = jdbc.queryForObject("INSERT INTO compras_anunciadas (creado_en, agencia_id, numero_anuncio, fecha_anuncio,"
                + " cedula, nombres, tipo, direccion, especial, kilos, saldo, fecha_entrega, pr_alm_sana, pr_alm_defec,"
                + " bonificacion, costos, pr_inc_calidad, pr_base_cps, creado_por_usuario_id) VALUES (now(), ?, 1, CURRENT_DATE,"
                + " ?, 'PRUEBA', 'C', 'VEREDA', 'RN', 100, 100, CURRENT_DATE, 0, 0, 0, 0, 0, 0, ?) RETURNING id",
                Long.class, AGENCY, SELLER, owner.getId());
        String other = token(user("prueba_idor_cupo", otherAgency()));

        mockMvc.perform(get("/api/announcement-quotas/987654").header("Authorization", other))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/purchases/dry-coffee/special-info?fundId=" + rp + "&specialType=RN&futurePurchaseId=" + future)
                .header("Authorization", other)).andExpect(status().isNotFound());
    }
}
