package com.cafeoccidente.backend.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * El perfil de produccion arranca con las mismas variables que dev (base, JWT y admin), confia en los encabezados
 * X-Forwarded-* de Caddy y no manda la traza de un error interno. Los mensajes de negocio (ApiException y los del
 * GlobalExceptionHandler) siguen llegando a la pantalla: include-message solo toca la respuesta de error por defecto.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("prod")
@Transactional
class ProdProfileIntegrationTest {

    @Autowired
    private Environment environment;
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

    @Test
    void prodProfileStartsBehindTheProxyWithoutLeakingErrors() {
        assertThat(environment.getProperty("server.forward-headers-strategy")).isEqualTo("framework");
        assertThat(environment.getProperty("server.error.include-stacktrace")).isEqualTo("never");
        assertThat(environment.getProperty("server.error.include-message")).isEqualTo("never");
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(environment.getProperty("jwt.secret")).isNotBlank();
    }

    @Test
    void businessMessagesStillReachTheScreen() throws Exception {
        User user = new User();
        user.setUsername("prod_mensajes");
        user.setPasswordHash(passwordEncoder.encode("clave-prod1"));
        user.setRole(roles.findByName(Roles.ADMINISTRADOR).orElseThrow());
        user.setAgency(agencies.findById(1L).orElseThrow());
        String token = "Bearer " + jwtService.generateToken(new SecurityUser(users.save(user)));

        mockMvc.perform(get("/api/purchases/dry-coffee/{id}", Long.MAX_VALUE).header("Authorization", token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Compra de cafe seco no encontrada"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"prod_mensajes\",\"password\":\"otra-clave\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Usuario o contrasena invalidos"));
    }
}
