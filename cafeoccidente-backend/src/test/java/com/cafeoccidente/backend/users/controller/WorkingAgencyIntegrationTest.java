package com.cafeoccidente.backend.users.controller;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * HU-06 (RF-05.1) contra la base real y con JWT de verdad: el filtro relee el usuario en cada request,
 * asi que la agencia que se guarda con PUT aplica en la siguiente operacion. Usa las agencias de la
 * semilla (1 Buesaco, 2 Ancuya, 3 Consaca, 4 El Tambo). Transaccional: no deja nada.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WorkingAgencyIntegrationTest {

    private static final String PASSWORD = "clave-de-prueba-hu06";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private RoleRepository roles;
    @Autowired
    private AgencyRepository agencies;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private JdbcTemplate jdbc;

    private String signIn(String username, String role) {
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setRole(roles.findByName(role).orElseThrow());
        user.setAgency(agencies.findById(1L).orElseThrow());
        return "Bearer " + jwtService.generateToken(new SecurityUser(users.save(user)));
    }

    private ResultActions chooseAgency(String token, long agencyId) throws Exception {
        return mockMvc.perform(put("/api/me/working-agency").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"agencyId\": " + agencyId + "}"));
    }

    /** Alta rapida de conductor: la agencia del tercero la pone el servidor, aunque el body mande otra. */
    private Long conductorAgency(String token, String idNumber) throws Exception {
        mockMvc.perform(post("/api/growers").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idNumber\": \"" + idNumber + "\", \"firstName\": \"PRUEBA\","
                                + " \"lastName\": \"HU06\", \"agencyId\": 3}"))
                .andExpect(status().isCreated());
        return jdbc.queryForObject("SELECT agencia_id FROM terceros WHERE identificacion = ?", Long.class, idNumber);
    }

    @Test
    void ca1TheChosenAgencyAppliesToTheNextOperations() throws Exception {
        String admin = signIn("hu06_admin", Roles.ADMINISTRADOR);

        chooseAgency(admin, 2).andExpect(status().isOk()).andExpect(jsonPath("$.agencyId").value(2));

        mockMvc.perform(get("/api/me").header("Authorization", admin))
                .andExpect(jsonPath("$.agencyId").value(2))
                .andExpect(jsonPath("$.agencyName").value("Ancuya"));
        assertThat(conductorAgency(admin, "990000601")).isEqualTo(2L);
    }

    /** CA-1 en compras: el consecutivo de factura sale del Registro de Control de la agencia de trabajo. */
    @Test
    void ca1InvoiceNumbersComeFromTheWorkingAgency() throws Exception {
        String admin = signIn("hu06_admin", Roles.ADMINISTRADOR);

        chooseAgency(admin, 4).andExpect(status().isOk());
        for (String module : new String[] {"dry-coffee", "green-coffee", "husk", "other-coffee", "ferti-futuro"}) {
            mockMvc.perform(get("/api/purchases/" + module + "/next-invoice-number").header("Authorization", admin))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.prefix").value("SDTA"));
        }
        chooseAgency(admin, 1).andExpect(status().isOk());
        mockMvc.perform(get("/api/purchases/dry-coffee/next-invoice-number").header("Authorization", admin))
                .andExpect(jsonPath("$.prefix").value("SDBU"));
    }

    /** CA-1 en suministros, empaques y consultas de anuncios. */
    @Test
    void ca1SuppliesAndQueriesUseTheWorkingAgency() throws Exception {
        String admin = signIn("hu06_admin", Roles.ADMINISTRADOR);
        chooseAgency(admin, 4).andExpect(status().isOk());

        mockMvc.perform(post("/api/supplies/petty-cash").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transactionId\": \"HU06-CAJA\", \"idNumber\": \"123456\", \"amount\": 1000, \"agencyId\": 1}"))
                .andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT agencia_id FROM movimientos_caja_menor WHERE id_transaccion = 'HU06-CAJA'",
                Long.class)).isEqualTo(4L);

        mockMvc.perform(post("/api/supplies/packaging").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transactionId\": \"HU06-EMPAQUE\", \"packagingType\": \"NUEVO\", \"idNumber\": \"123456\","
                                + " \"quantity\": 5, \"agencyId\": 1}"))
                .andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT agencia_id FROM empaques WHERE id_transaccion = 'HU06-EMPAQUE'",
                Long.class)).isEqualTo(4L);

        Integer numbered = jdbc.queryForObject("SELECT count(*) FROM anuncios_agencias WHERE agencia_id = 4", Integer.class);
        mockMvc.perform(get("/api/announcements/history").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(numbered));
    }

    @Test
    void ca3ChangingTheAgencyMovesTheNextOperation() throws Exception {
        String admin = signIn("hu06_admin", Roles.ADMINISTRADOR);
        chooseAgency(admin, 2).andExpect(status().isOk());
        assertThat(conductorAgency(admin, "990000602")).isEqualTo(2L);

        chooseAgency(admin, 4).andExpect(status().isOk());
        assertThat(conductorAgency(admin, "990000603")).isEqualTo(4L);
    }

    @Test
    void ca4LoginSelectsTheLastAgencyUsed() throws Exception {
        String admin = signIn("hu06_admin", Roles.ADMINISTRADOR);
        chooseAgency(admin, 2).andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"hu06_admin\", \"password\": \"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roleName").value(Roles.ADMINISTRADOR))
                .andExpect(jsonPath("$.agencyId").value(2))
                .andExpect(jsonPath("$.agencyName").value("Ancuya"));
    }

    @Test
    void onlyTheAdministratorChoosesAnAgency() throws Exception {
        chooseAgency(signIn("hu06_usuario", Roles.USUARIO), 2).andExpect(status().isForbidden());
        chooseAgency(signIn("hu06_operaciones", Roles.OPERACIONES), 2).andExpect(status().isForbidden());
    }

    @Test
    void anInactiveOrMissingAgencyCannotBeChosen() throws Exception {
        jdbc.update("UPDATE agencias SET activa = false WHERE id = 4");
        String admin = signIn("hu06_admin", Roles.ADMINISTRADOR);

        chooseAgency(admin, 4).andExpect(status().isBadRequest());
        chooseAgency(admin, 999_999).andExpect(status().isNotFound());
    }

    /** HU-01 CA-2: el Usuario registra en su agencia asignada aunque el body pida otra. */
    @Test
    void theUserRegistersInHisAgencyWhateverTheBodySays() throws Exception {
        assertThat(conductorAgency(signIn("hu06_usuario", Roles.USUARIO), "990000604")).isEqualTo(1L);
    }
}
