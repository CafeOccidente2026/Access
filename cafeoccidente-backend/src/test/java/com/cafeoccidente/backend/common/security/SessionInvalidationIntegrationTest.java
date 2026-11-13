package com.cafeoccidente.backend.common.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import com.cafeoccidente.backend.purchases.shared.repository.AgencyRepository;
import com.cafeoccidente.backend.users.entity.User;
import com.cafeoccidente.backend.users.repository.RoleRepository;
import com.cafeoccidente.backend.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * S6 (respuesta del 13-nov): al cambiarle al usuario la contrasena, el rol o la agencia, o al desactivarlo, las sesiones
 * que tenia abiertas dejan de valer (401) aunque el token no haya vencido. Editar sin cambiar nada de eso no las cierra.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SessionInvalidationIntegrationTest {

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
    @Value("${jwt.secret}")
    private String secret;

    private User ana;
    private String anaToken;
    private String adminToken;
    private long usuarioRole;

    private User user(String username, String role) {
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode("clave-s6a1"));
        user.setRole(roles.findByName(role).orElseThrow());
        user.setAgency(agencies.findById(1L).orElseThrow());
        return users.saveAndFlush(user);
    }

    private String token(User user) {
        return "Bearer " + jwtService.generateToken(new SecurityUser(user));
    }

    @BeforeEach
    void setUp() {
        ana = user("s6_ana", Roles.USUARIO);
        anaToken = token(ana);
        adminToken = token(user("s6_admin", Roles.ADMINISTRADOR));
        usuarioRole = ana.getRole().getId();
    }

    private void edit(long roleId, long agencyId, boolean active, String password) throws Exception {
        String body = "{\"roleId\": " + roleId + ", \"agencyId\": " + agencyId + ", \"active\": " + active
                + ", \"password\": " + (password == null ? "null" : "\"" + password + "\"") + "}";
        mockMvc.perform(put("/api/users/" + ana.getId()).header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
    }

    private void anaSession(boolean valid) throws Exception {
        mockMvc.perform(get("/api/me").header("Authorization", anaToken))
                .andExpect(valid ? status().isOk() : status().isUnauthorized());
    }

    @Test
    void editingWithoutChangesKeepsTheSession() throws Exception {
        edit(usuarioRole, 1, true, "");
        anaSession(true);
    }

    @Test
    void aNewPasswordClosesTheSessions() throws Exception {
        edit(usuarioRole, 1, true, "nueva-s6b2");
        anaSession(false);
    }

    @Test
    void anotherRoleClosesTheSessions() throws Exception {
        edit(roles.findByName(Roles.OPERACIONES).orElseThrow().getId(), 1, true, null);
        anaSession(false);
    }

    @Test
    void anotherAgencyClosesTheSessions() throws Exception {
        edit(usuarioRole, 2, true, null);
        anaSession(false);
    }

    @Test
    void deactivatingClosesTheSessionsAndReactivatingDoesNotBringThemBack() throws Exception {
        edit(usuarioRole, 1, false, null);
        anaSession(false);
        edit(usuarioRole, 1, true, null);
        anaSession(false);
    }

    @Test
    void aTokenWithoutVersionIsRejected() throws Exception {
        // Como los tokens emitidos antes de S6: firmados con la misma clave, pero sin la version.
        String old = Jwts.builder().subject(ana.getUsername()).claim("userId", ana.getId())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8))).compact();
        mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + old)).andExpect(status().isUnauthorized());
    }
}
