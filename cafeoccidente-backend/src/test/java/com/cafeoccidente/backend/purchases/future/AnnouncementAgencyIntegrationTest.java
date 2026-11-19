package com.cafeoccidente.backend.purchases.future;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cafeoccidente.backend.common.security.JwtService;
import com.cafeoccidente.backend.common.security.Roles;
import com.cafeoccidente.backend.common.security.SecurityUser;
import com.cafeoccidente.backend.purchases.shared.repository.AgencyRepository;
import com.cafeoccidente.backend.users.entity.User;
import com.cafeoccidente.backend.users.repository.RoleRepository;
import com.cafeoccidente.backend.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Prueba del 19-nov: cada agencia ve y usa solo sus anuncios. El anuncio de prueba es solo de El Tambo (agencia 4); un
 * usuario de Buesaco (agencia 1) no lo ve en sus listas y el servidor le rechaza la compra, el cupo y la compra a
 * futuro que lo usen aunque los mande a mano por la API. Sobre la semilla. Transaccional.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@SuppressWarnings("null")
class AnnouncementAgencyIntegrationTest {

    private static final long BUESACO = 1L;
    private static final long EL_TAMBO = 4L;
    private static final String SELLER = "990004444";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private UserRepository users;
    @Autowired
    private RoleRepository roles;
    @Autowired
    private AgencyRepository agencies;
    @Autowired
    private JdbcTemplate jdbc;

    private Long rp;
    /** Numero del anuncio que solo existe en El Tambo. */
    private int tamboNumber;
    private String buesaco;
    private String tambo;

    private String token(String username, long agencyId, String role) {
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash("x");
        user.setRole(roles.findByName(role).orElseThrow());
        user.setAgency(agencies.findById(agencyId).orElseThrow());
        return "Bearer " + jwtService.generateToken(new SecurityUser(users.save(user)));
    }

    @BeforeEach
    void announcementOfElTambo() {
        rp = jdbc.queryForObject("SELECT id FROM fondos WHERE codigo = 'RP'", Long.class);
        Long id = jdbc.queryForObject("INSERT INTO anuncios (fecha_anuncio, pr_base_cps, pr_alm_defec, sobrepr_carga_esp,"
                + " pr_inc_calidad, fondo_id, especial, activo) VALUES (CURRENT_DATE, 1546900, 6000, 80000, 50, ?, 'RN', true)"
                + " RETURNING id", Long.class, rp);
        // Mayor que cualquier numero de las dos agencias: en Buesaco no existe.
        tamboNumber = jdbc.queryForObject("SELECT COALESCE(MAX(numero_anuncio), 0) + 1 FROM anuncios_agencias"
                + " WHERE agencia_id IN (?, ?)", Integer.class, BUESACO, EL_TAMBO);
        jdbc.update("INSERT INTO anuncios_agencias (agencia_id, anuncio_id, numero_anuncio, fecha_asignacion)"
                + " VALUES (?, ?, ?, CURRENT_DATE)", EL_TAMBO, id, tamboNumber);
        jdbc.update("INSERT INTO terceros (identificacion, primer_nombre, primer_apellido, agencia_id, direccion, telefono, tipo,"
                + " asociacion) VALUES (?, 'PRUEBA', 'AGENCIA', ?, 'VEREDA', '3001112233', 'C', false)", SELLER, BUESACO);
        buesaco = token("prueba_anuncio_bu", BUESACO, Roles.USUARIO);
        tambo = token("prueba_anuncio_ta", EL_TAMBO, Roles.USUARIO);
    }

    @Test
    void theListsOfBuesacoDoNotShowTheAnnouncementOfElTambo() throws Exception {
        String number = "-" + tamboNumber;
        mockMvc.perform(get("/api/purchases/future/announcements").param("specialType", "RN").header("Authorization", tambo))
                .andExpect(status().isOk()).andExpect(jsonPath("$[*].announcementNumber", hasItem(endsWith(number))));
        mockMvc.perform(get("/api/purchases/future/announcements").param("specialType", "RN").header("Authorization", buesaco))
                .andExpect(status().isOk()).andExpect(jsonPath("$[*].announcementNumber", not(hasItem(endsWith(number)))));
        mockMvc.perform(get("/api/announcement-quotas/available").header("Authorization", buesaco))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].announcement.announcementNumber", not(hasItem(endsWith(number)))));
    }

    @Test
    void buesacoCannotReadOrAssignAQuotaToTheAnnouncementOfElTambo() throws Exception {
        String url = "/api/announcement-quotas/" + tamboNumber;
        mockMvc.perform(put(url).header("Authorization", buesaco).contentType(MediaType.APPLICATION_JSON)
                .content("{\"assignedQuota\": 500}")).andExpect(status().isNotFound());
        mockMvc.perform(put(url).header("Authorization", tambo).contentType(MediaType.APPLICATION_JSON)
                .content("{\"assignedQuota\": 700}")).andExpect(status().isOk());
        mockMvc.perform(get(url).header("Authorization", tambo)).andExpect(status().isOk());
        mockMvc.perform(get(url).header("Authorization", buesaco)).andExpect(status().isNotFound());
        Integer quotas = jdbc.queryForObject("SELECT count(*) FROM cupos_anuncios c JOIN anuncios_agencias n"
                + " ON n.id = c.anuncio_agencia_id WHERE n.numero_anuncio = ? AND c.cupo = 500", Integer.class, tamboNumber);
        org.assertj.core.api.Assertions.assertThat(quotas).isZero();
    }

    @Test
    void buesacoCannotBuyOrAnnounceWithTheAnnouncementOfElTambo() throws Exception {
        mockMvc.perform(get("/api/purchases/dry-coffee/special-info").header("Authorization", buesaco)
                .param("fundId", rp.toString()).param("specialType", "RN")
                .param("announcementNumber", String.valueOf(tamboNumber))).andExpect(status().isNotFound());
        String purchase = "{\"fundId\": %d, \"specialType\": \"RN\", \"idNumber\": \"%s\", \"bagsCount\": 5, \"grossKg\": 1000,"
                + " \"tareKg\": 0, \"totalStoredWeight\": 200, \"defectiveStoredWeight\": 10, \"penalty\": 0,"
                + " \"freightDiscount\": 0, \"otherDiscounts\": 0, \"quotaAnnouncementNumber\": %d, \"deliveryKg\": 100}";
        mockMvc.perform(post("/api/purchases/dry-coffee").header("Authorization", buesaco)
                .contentType(MediaType.APPLICATION_JSON).content(purchase.formatted(rp, SELLER, tamboNumber)))
                .andExpect(status().isNotFound());
        String future = "{\"idNumber\": \"%s\", \"specialType\": \"RN\", \"announcementNumber\": %d, \"announcedKg\": 100,"
                + " \"deliveryDate\": \"2099-01-01\"}";
        mockMvc.perform(post("/api/purchases/future").header("Authorization", buesaco)
                .contentType(MediaType.APPLICATION_JSON).content(future.formatted(SELLER, tamboNumber)))
                .andExpect(status().isNotFound());
        Integer saved = jdbc.queryForObject("SELECT (SELECT count(*) FROM compras_cafe_seco WHERE cedula = ?)"
                + " + (SELECT count(*) FROM compras_anunciadas WHERE cedula = ?)", Integer.class, SELLER, SELLER);
        org.assertj.core.api.Assertions.assertThat(saved).isZero();
    }

    @Test
    void buesacoCannotBillAFuturePurchaseOfElTambo() throws Exception {
        Long owner = jdbc.queryForObject("SELECT id FROM usuarios WHERE usuario = 'prueba_anuncio_ta'", Long.class);
        Long future = jdbc.queryForObject("INSERT INTO compras_anunciadas (creado_en, agencia_id, numero_anuncio, fecha_anuncio,"
                + " cedula, nombres, tipo, direccion, especial, kilos, saldo, fecha_entrega, pr_alm_sana, pr_alm_defec,"
                + " bonificacion, costos, pr_inc_calidad, pr_base_cps, creado_por_usuario_id) VALUES (now(), ?, ?, CURRENT_DATE,"
                + " ?, 'PRUEBA', 'C', 'VEREDA', 'RN', 100, 100, CURRENT_DATE, 0, 0, 0, 0, 0, 0, ?) RETURNING id",
                Long.class, EL_TAMBO, "SDTA-" + tamboNumber, SELLER, owner);
        String purchase = "{\"fundId\": %d, \"specialType\": \"RN\", \"idNumber\": \"%s\", \"bagsCount\": 5, \"grossKg\": 1000,"
                + " \"tareKg\": 0, \"totalStoredWeight\": 200, \"defectiveStoredWeight\": 10, \"penalty\": 0,"
                + " \"freightDiscount\": 0, \"otherDiscounts\": 0, \"futurePurchaseId\": %d, \"deliveryKg\": 100}";
        mockMvc.perform(post("/api/purchases/dry-coffee").header("Authorization", buesaco)
                .contentType(MediaType.APPLICATION_JSON).content(purchase.formatted(rp, SELLER, future)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/purchases/future/pending").header("Authorization", buesaco))
                .andExpect(status().isOk()).andExpect(jsonPath("$[*].announcementNumber", not(hasItem(endsWith("-" + tamboNumber)))));
    }
}
