package com.cafeoccidente.backend.users.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cafeoccidente.backend.common.security.JwtService;
import com.cafeoccidente.backend.common.security.LoginThrottle;
import com.cafeoccidente.backend.common.security.Roles;
import com.cafeoccidente.backend.common.security.SecurityUser;
import com.cafeoccidente.backend.purchases.shared.repository.AgencyRepository;
import com.cafeoccidente.backend.users.entity.User;
import com.cafeoccidente.backend.users.repository.RoleRepository;
import com.cafeoccidente.backend.users.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
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
 * S4: 5 intentos fallidos dejan al usuario esperando 15 min (429 con cuanto falta), otro usuario sigue entrando y el
 * Administrador lo desbloquea desde Usuarios. Cada test usa sus IP para no cruzarse con otros tests del contexto.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LoginLimitIntegrationTest {

    private static final String PASSWORD = "clave-s4a1";

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
    private LoginThrottle throttle;

    private User ana;
    private User admin;

    private User user(String username, String role) {
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setRole(roles.findByName(role).orElseThrow());
        user.setAgency(agencies.findById(1L).orElseThrow());
        return users.saveAndFlush(user);
    }

    @BeforeEach
    void createUsers() {
        ana = user("s4_ana", Roles.USUARIO);
        user("s4_beto", Roles.USUARIO);
        admin = user("s4_admin", Roles.ADMINISTRADOR);
    }

    @AfterEach
    void forget() {
        for (String name : new String[] {"s4_ana", "s4_beto", "s4_x1", "s4_x2", "s4_x3", "s4_x4", "s4_x5"}) {
            throttle.unlock(name);
        }
    }

    private ResultActions login(String username, String password, String ip) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\": \"" + username + "\", \"password\": \"" + password + "\"}")
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                }));
    }

    private void failFiveTimes(String ip) throws Exception {
        for (int i = 0; i < 5; i++) {
            login("s4_ana", "mala", ip).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void theSixthAttemptWaitsEvenWithTheRightPassword() throws Exception {
        failFiveTimes("203.0.113.1");
        login("s4_ana", PASSWORD, "203.0.113.2")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("LOGIN_LOCKED"))
                .andExpect(jsonPath("$.message").value("Demasiados intentos fallidos. Intente de nuevo en 15 minutos."));
        login("s4_beto", PASSWORD, "203.0.113.3").andExpect(status().isOk());
    }

    @Test
    void fiveFailuresFromOneIpWaitForAnyUserFromThatIp() throws Exception {
        for (int i = 1; i <= 5; i++) {
            login("s4_x" + i, "mala", "203.0.113.20").andExpect(status().isUnauthorized());
        }
        login("s4_beto", PASSWORD, "203.0.113.20").andExpect(status().isTooManyRequests());
        login("s4_beto", PASSWORD, "203.0.113.21").andExpect(status().isOk());
    }

    @Test
    void theAdministratorUnlocksTheUser() throws Exception {
        failFiveTimes("203.0.113.10");
        String token = "Bearer " + jwtService.generateToken(new SecurityUser(admin));
        mockMvc.perform(post("/api/users/" + ana.getId() + "/unlock").header("Authorization", token))
                .andExpect(status().isNoContent());
        login("s4_ana", PASSWORD, "203.0.113.10").andExpect(status().isOk());
    }
}
