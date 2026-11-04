package com.cafeoccidente.backend.common.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.transaction.annotation.Transactional;

/**
 * Token de un usuario borrado o desactivado: deja de valer de inmediato (401) sin esperar a que venza,
 * y si el navegador lo sigue mandando no impide que otro usuario entre. Usa la semilla de V2 (corre en CI).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StaleTokenIntegrationTest {

    private static final String PASSWORD = "clave-token1";

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

    private User stale;
    private String staleToken;

    @BeforeEach
    void setUp() {
        user("token_otro");
        stale = user("token_viejo");
        staleToken = "Bearer " + jwtService.generateToken(new SecurityUser(stale));
    }

    private User user(String username) {
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setRole(roles.findByName(Roles.ADMINISTRADOR).orElseThrow());
        user.setAgency(agencies.findById(1L).orElseThrow());
        return users.save(user);
    }

    @Test
    void theTokenOfAValidUserStillWorks() throws Exception {
        mockMvc.perform(get("/api/agencies").header("Authorization", staleToken)).andExpect(status().isOk());
    }

    @Test
    void theTokenOfADeletedUserIsRejectedAndDoesNotBlockTheLogin() throws Exception {
        users.delete(stale);
        users.flush();
        assertStaleTokenRejected();
    }

    @Test
    void theTokenOfADeactivatedUserIsRejectedAndDoesNotBlockTheLogin() throws Exception {
        stale.setActive(false);
        users.saveAndFlush(stale);
        assertStaleTokenRejected();
    }

    @Test
    void aRequestWithoutTokenGets401() throws Exception {
        mockMvc.perform(get("/api/agencies")).andExpect(status().isUnauthorized());
    }

    private void assertStaleTokenRejected() throws Exception {
        mockMvc.perform(get("/api/agencies").header("Authorization", staleToken)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/login").header("Authorization", staleToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"token_otro\", \"password\": \"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("token_otro"));
    }
}
