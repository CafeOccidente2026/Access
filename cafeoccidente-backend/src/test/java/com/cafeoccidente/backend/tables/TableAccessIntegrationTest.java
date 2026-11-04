package com.cafeoccidente.backend.tables;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cafeoccidente.backend.common.security.JwtService;
import com.cafeoccidente.backend.common.security.Roles;
import com.cafeoccidente.backend.common.security.SecurityUser;
import com.cafeoccidente.backend.permissions.entity.Screen;
import com.cafeoccidente.backend.purchases.shared.repository.AgencyRepository;
import com.cafeoccidente.backend.users.entity.User;
import com.cafeoccidente.backend.users.repository.RoleRepository;
import com.cafeoccidente.backend.users.repository.UserRepository;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * HU-04 (RF-03) con JWT real: lista de tablas, consulta filtrada por la agencia de trabajo y solo lectura.
 * Crea sus propias filas de empaques sobre la semilla de V2 (corre en CI). Transaccional: no deja nada.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TableAccessIntegrationTest {

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
    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    private String admin;

    @BeforeEach
    void setUp() {
        admin = token(user("hu04_admin", Roles.ADMINISTRADOR));
        packaging(1L, "HU04-B1");
        packaging(4L, "HU04-TAMBO");
    }

    private User user(String username, String role) {
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode("clave-hu04"));
        user.setRole(roles.findByName(role).orElseThrow());
        user.setAgency(agencies.findById(1L).orElseThrow());
        return users.save(user);
    }

    private String token(User user) {
        return "Bearer " + jwtService.generateToken(new SecurityUser(user));
    }

    private void packaging(Long agencyId, String transactionId) {
        jdbc.update("INSERT INTO empaques (id_transaccion, agencia_id, tipo_empaque, fecha, cedula, entradas, salidas,"
                + " creado_en) VALUES (?, ?, 'NUEVO', CURRENT_DATE, '123', 5, 0, now())", transactionId, agencyId);
    }

    @Test
    void ca1ListsTheTablesAvailableForConsultation() throws Exception {
        mockMvc.perform(get("/api/tables").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(10))
                .andExpect(jsonPath("$[0]").value("CAJA_MENOR"))
                .andExpect(jsonPath("$[9]").value("CAJA"));
    }

    @Test
    void ca2OnlyTheRowsOfTheWorkingAgency() throws Exception {
        long own = jdbc.queryForObject("SELECT count(*) FROM empaques WHERE agencia_id = 1", Long.class);
        mockMvc.perform(get("/api/tables/EMPAQUE?size=200").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.columns[2]").value("agencia_id"))
                .andExpect(jsonPath("$.rows[*][2]").value(everyItem(is("1"))))
                .andExpect(jsonPath("$.rows[0][1]").value("HU04-B1"))
                .andExpect(jsonPath("$.total").value(own));
    }

    @Test
    void productsAreCommonToAllAgencies() throws Exception {
        long all = jdbc.queryForObject("SELECT count(*) FROM productos", Long.class);
        mockMvc.perform(get("/api/tables/PRODUCTOS").header("Authorization", admin))
                .andExpect(jsonPath("$.total").value(all));
    }

    @Test
    void anUnknownTableIsNotFound() throws Exception {
        mockMvc.perform(get("/api/tables/usuarios").header("Authorization", admin)).andExpect(status().isNotFound());
    }

    @Test
    void readOnlyThereIsNoWriteVerb() throws Exception {
        Set<String> methods = new HashSet<>();
        handlerMapping.getHandlerMethods().forEach((info, handler) -> {
            if (info.getPatternValues().stream().anyMatch(p -> p.startsWith("/api/tables"))) {
                info.getMethodsCondition().getMethods().forEach(m -> methods.add(m.name()));
            }
        });
        assertThat(methods).containsExactly("GET");
        mockMvc.perform(post("/api/tables/EMPAQUE").header("Authorization", admin)).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(put("/api/tables/EMPAQUE").header("Authorization", admin)).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(delete("/api/tables/EMPAQUE").header("Authorization", admin)).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void aUserNeedsTheScreenAndOnlySeesTheirOwnAgency() throws Exception {
        User clerk = user("hu04_usuario", Roles.USUARIO);
        mockMvc.perform(get("/api/tables/EMPAQUE").header("Authorization", token(clerk))).andExpect(status().isForbidden());

        jdbc.update("INSERT INTO permisos (usuario_id, pantalla, concedido_por) VALUES (?, ?, ?)",
                clerk.getId(), Screen.ACCESO_TABLAS.name(), clerk.getId());
        mockMvc.perform(get("/api/tables/EMPAQUE?size=200").header("Authorization", token(clerk)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[*][2]").value(everyItem(is("1"))));
    }
}
