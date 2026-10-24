package com.cafeoccidente.backend.permissions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import java.util.List;
import java.util.Map;
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
 * HU-03 (RF-02) y HU-01 CA-4 con JWT real: el permiso vale en el backend en la siguiente peticion,
 * con el mismo token, y al retirarlo se pierde igual. Usuarios propios sobre la semilla de V2 (corre en
 * CI). Transaccional: no deja nada.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PermissionIntegrationTest {

    private static final String ANNULMENT = "/api/purchases/annulment?invoiceNumber=1";

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
    private User clerk;
    private String clerkToken;

    @BeforeEach
    void signIn() {
        admin = token(user("hu03_admin", Roles.ADMINISTRADOR));
        clerk = user("hu03_usuario", Roles.USUARIO);
        clerkToken = token(clerk);
    }

    private User user(String username, String role) {
        return user(username, role, 1L);
    }

    private User user(String username, String role, Long agencyId) {
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode("clave-hu03"));
        user.setRole(roles.findByName(role).orElseThrow());
        user.setAgency(agencies.findById(agencyId).orElseThrow());
        return users.save(user);
    }

    private String token(User user) {
        return "Bearer " + jwtService.generateToken(new SecurityUser(user));
    }

    private ResultActions grant(Long userId, String... screens) throws Exception {
        String list = String.join("\", \"", screens);
        return mockMvc.perform(post("/api/permissions").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\": " + userId + ", \"screens\": [\"" + list + "\"]}"));
    }

    private ResultActions revoke(Long userId, String screen) throws Exception {
        return mockMvc.perform(delete("/api/permissions/" + userId + "/" + screen).header("Authorization", admin));
    }

    @Test
    void ca2AndHu01Ca4TheGrantedScreenOpensWithTheSameToken() throws Exception {
        mockMvc.perform(get(ANNULMENT).header("Authorization", clerkToken)).andExpect(status().isForbidden());

        grant(clerk.getId(), "ANULAR_DOCUMENTO")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].screen").value("ANULAR_DOCUMENTO"));

        mockMvc.perform(get(ANNULMENT).header("Authorization", clerkToken)).andExpect(status().isOk());
        mockMvc.perform(get("/api/me").header("Authorization", clerkToken))
                .andExpect(jsonPath("$.screens[0]").value("ANULAR_DOCUMENTO"));
    }

    @Test
    void ca3RevokingReturnsTheUserToTheAccessOfTheirRole() throws Exception {
        grant(clerk.getId(), "ANULAR_DOCUMENTO", "HISTORIAL").andExpect(status().isOk());

        revoke(clerk.getId(), "ANULAR_DOCUMENTO").andExpect(status().isNoContent());

        mockMvc.perform(get(ANNULMENT).header("Authorization", clerkToken)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/permissions?userId=" + clerk.getId()).header("Authorization", admin))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].screen").value("HISTORIAL"));
    }

    @Test
    void grantingTheSameScreenTwiceDoesNotDuplicateIt() throws Exception {
        grant(clerk.getId(), "HISTORIAL", "HISTORIAL").andExpect(status().isOk());
        grant(clerk.getId(), "HISTORIAL").andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void onlyScreensOfTheCatalogAndNeverToTheAdministrator() throws Exception {
        grant(clerk.getId(), "USUARIOS").andExpect(status().isBadRequest());
        grant(clerk.getId(), "PERMISOS").andExpect(status().isBadRequest());
        grant(user("hu03_admin2", Roles.ADMINISTRADOR).getId(), "HISTORIAL").andExpect(status().isBadRequest());
        grant(user("hu03_ops", Roles.OPERACIONES).getId(), "ACTUALIZAR_ANUNCIOS").andExpect(status().isBadRequest());
        revoke(clerk.getId(), "HISTORIAL").andExpect(status().isNotFound());
    }

    @Test
    void grantingAndRevokingAreInTheHistory() throws Exception {
        grant(clerk.getId(), "ANULAR_DOCUMENTO").andExpect(status().isOk());
        revoke(clerk.getId(), "ANULAR_DOCUMENTO").andExpect(status().isNoContent());

        List<Map<String, Object>> rows = jdbc.queryForList("SELECT u.usuario, h.accion, h.agencia_id,"
                + " h.despues::text AS despues FROM historial_cambios h JOIN usuarios u ON u.id = h.usuario_id"
                + " WHERE h.entidad = 'PERMISO' AND h.entidad_id = ? ORDER BY h.id", clerk.getId());
        assertThat(rows).extracting(row -> row.get("accion")).containsExactly("CONCEDER", "RETIRAR");
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.get("usuario")).isEqualTo("hu03_admin");
            assertThat(row.get("agencia_id")).isEqualTo(1L);
            assertThat((String) row.get("despues")).contains("Anular Documento").contains("hu03_usuario");
        });
    }

    @Test
    void withTheControlRecordScreenOnlyTheirOwnAgency() throws Exception {
        grant(clerk.getId(), "REGISTRO_CONTROL").andExpect(status().isOk());

        mockMvc.perform(get("/api/control-records/1").header("Authorization", clerkToken)).andExpect(status().isOk());
        mockMvc.perform(get("/api/control-records/4").header("Authorization", clerkToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/control-records").header("Authorization", clerkToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void withTheHistoryScreenOnlyTheChangesOfTheirOwnAgency() throws Exception {
        Long otherAgencyUser = user("hu03_tambo", Roles.USUARIO, 4L).getId();
        grant(otherAgencyUser, "ANULAR_DOCUMENTO").andExpect(status().isOk()); // fila de El Tambo
        grant(clerk.getId(), "HISTORIAL").andExpect(status().isOk()); // fila de Buesaco 1

        mockMvc.perform(get("/api/history?agencyId=4").header("Authorization", clerkToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].agencyName").value(everyItem(is("Buesaco 1"))))
                .andExpect(jsonPath("$.total").value(1));
    }
}
