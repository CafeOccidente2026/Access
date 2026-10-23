package com.cafeoccidente.backend.users.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cafeoccidente.backend.common.security.Roles;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * Login de un usuario desactivado: con su contrasena correcta, 401 con el aviso de desactivado; con una
 * contrasena equivocada, el mensaje de siempre (no revela que existe ni su estado). Semilla de V2 (CI).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DisabledUserLoginIntegrationTest {

    private static final String PASSWORD = "clave-inact1";

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

    @BeforeEach
    void createDisabledUser() {
        User user = new User();
        user.setUsername("login_inactivo");
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setRole(roles.findByName(Roles.USUARIO).orElseThrow());
        user.setAgency(agencies.findById(1L).orElseThrow());
        user.setActive(false);
        users.saveAndFlush(user);
    }

    private ResultActions login(String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\": \"login_inactivo\", \"password\": \"" + password + "\"}"));
    }

    @Test
    void withTheRightPasswordItIsToldTheUserIsDisabled() throws Exception {
        login(PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("USER_DISABLED"))
                .andExpect(jsonPath("$.message").value("Su usuario está desactivado. Comuníquese con el Administrador."));
    }

    @Test
    void withAWrongPasswordItGetsTheUsualMessage() throws Exception {
        login("otra-clave1")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.message").value("Usuario o contrasena invalidos"));
    }
}
