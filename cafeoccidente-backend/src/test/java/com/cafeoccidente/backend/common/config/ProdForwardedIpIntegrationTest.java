package com.cafeoccidente.backend.common.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cafeoccidente.backend.common.security.LoginThrottle;
import com.cafeoccidente.backend.common.security.Roles;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * Produccion con forward-headers-strategy framework (decision de R0): detras de Caddy, el limite de login por IP (S4)
 * cuenta la IP real del cliente, la que Caddy pone en X-Forwarded-For, y no la de Caddy. Caddy reemplaza el
 * X-Forwarded-For que mande el cliente (verificado en el compose de prueba), asi que un cliente no puede cambiarla.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("prod")
@Transactional
class ProdForwardedIpIntegrationTest {

    private static final String CADDY = "172.18.0.5";
    private static final String PASSWORD = "clave-prod-ip1";

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
    private LoginThrottle throttle;

    @BeforeEach
    void createUser() {
        User user = new User();
        user.setUsername("prod_ip_ana");
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setRole(roles.findByName(Roles.USUARIO).orElseThrow());
        user.setAgency(agencies.findById(1L).orElseThrow());
        users.saveAndFlush(user);
    }

    @AfterEach
    void forget() {
        for (int i = 1; i <= 30; i++) {
            throttle.unlock("prod_ip_x" + i);
        }
    }

    private ResultActions login(String username, String password, String clientIp) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\": \"" + username + "\", \"password\": \"" + password + "\"}")
                .header("X-Forwarded-For", clientIp)
                .with(request -> {
                    request.setRemoteAddr(CADDY);
                    return request;
                }));
    }

    @Test
    void theLoginLimitCountsTheClientIpThatCaddySends() throws Exception {
        for (int i = 1; i <= 30; i++) {
            login("prod_ip_x" + i, "mala", "198.51.100.40").andExpect(status().isUnauthorized());
        }
        login("prod_ip_ana", PASSWORD, "198.51.100.40").andExpect(status().isTooManyRequests());
        // Otra IP detras del mismo Caddy sigue entrando: no se bloqueo la IP de Caddy.
        login("prod_ip_ana", PASSWORD, "198.51.100.41").andExpect(status().isOk());
    }
}
