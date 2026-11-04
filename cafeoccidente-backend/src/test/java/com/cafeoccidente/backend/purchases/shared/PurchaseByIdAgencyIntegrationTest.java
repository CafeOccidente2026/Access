package com.cafeoccidente.backend.purchases.shared;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 6g3 (seguridad): GET /{id} de seco, verde, pasilla y compras a futuro solo da compras de la agencia de
 * trabajo; la de otra agencia responde 404, como Consultar Compras. Sobre la semilla (agencia 1). Transaccional.
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
}
