package com.cafeoccidente.backend.purchases.shared.controller;

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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * HU-09 (RF-07, RN-07.4), D2 y una agencia sin Registro de Control (fase 3, C6), con JWT real sobre la
 * semilla de V2 (1 Buesaco con Registro, 2 Ancuya sin Registro), asi que corre tambien en CI.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AgencyIntegrationTest {

    private static final String NO_CONTROL_RECORD =
            "La agencia no tiene Registro de Control; pídale al Administrador que lo configure";

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

    private String admin;

    @BeforeEach
    void signIn() {
        User user = new User();
        user.setUsername("hu09_admin");
        user.setPasswordHash(passwordEncoder.encode("clave-hu09"));
        user.setRole(roles.findByName(Roles.ADMINISTRADOR).orElseThrow());
        user.setAgency(agencies.findById(1L).orElseThrow());
        admin = "Bearer " + jwtService.generateToken(new SecurityUser(users.save(user)));
    }

    private ResultActions create(String name) throws Exception {
        return mockMvc.perform(post("/api/agencies").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"" + name + "\"}"));
    }

    private long createdId(String name) throws Exception {
        create(name).andExpect(status().isCreated());
        return jdbc.queryForObject("SELECT id FROM agencias WHERE nombre = ?", Long.class, name);
    }

    @Test
    void ca1TheNewAgencyIsInTheListRightAwayWithoutControlRecord() throws Exception {
        create("Agencia Prueba HU09")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Agencia Prueba HU09"))
                .andExpect(jsonPath("$.hasControlRecord").value(false));
        mockMvc.perform(get("/api/agencies").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Agencia Prueba HU09')].hasControlRecord").value(false));
    }

    @Test
    void theNameIsRequiredAndUnique() throws Exception {
        create(" ").andExpect(status().isBadRequest());
        create("buesaco").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Ya existe una agencia con ese nombre"));
    }

    @Test
    void d2TheListTellsWhichAgenciesHaveControlRecordAndTheirSuggestedPrefix() throws Exception {
        mockMvc.perform(get("/api/agencies").header("Authorization", admin))
                .andExpect(jsonPath("$[?(@.id == 1)].hasControlRecord").value(true))
                .andExpect(jsonPath("$[?(@.id == 1)].suggestedPrefix").value((Object) null))
                .andExpect(jsonPath("$[?(@.id == 2)].hasControlRecord").value(false))
                .andExpect(jsonPath("$[?(@.id == 2)].suggestedPrefix").value("DCAN"));
    }

    @Test
    void c6AnAgencyWithoutControlRecordCannotBuyNorRegisterSupplies() throws Exception {
        long agencyId = createdId("Agencia Sin Registro");
        mockMvc.perform(put("/api/me/working-agency").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"agencyId\": " + agencyId + "}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/purchases/dry-coffee/next-invoice-number").header("Authorization", admin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(NO_CONTROL_RECORD));
        mockMvc.perform(post("/api/supplies/cash").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transactionId\": \"PRU-1\", \"fundCode\": \"RP\", \"idNumber\": \"1\","
                                + " \"amount\": 1000, \"paymentMethod\": \"EFECTIVO\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(NO_CONTROL_RECORD));
    }

    @Test
    void onlyTheAdministratorCreatesAgencies() throws Exception {
        User user = new User();
        user.setUsername("hu09_usuario");
        user.setPasswordHash(passwordEncoder.encode("clave-hu09"));
        user.setRole(roles.findByName(Roles.USUARIO).orElseThrow());
        user.setAgency(agencies.findById(1L).orElseThrow());
        String token = "Bearer " + jwtService.generateToken(new SecurityUser(users.save(user)));
        mockMvc.perform(post("/api/agencies").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"No Debe\"}"))
                .andExpect(status().isForbidden());
    }
}
