package com.cafeoccidente.backend.tables;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cafeoccidente.backend.common.security.JwtService;
import com.cafeoccidente.backend.common.security.Roles;
import com.cafeoccidente.backend.common.security.SecurityUser;
import com.cafeoccidente.backend.permissions.entity.Screen;
import com.cafeoccidente.backend.purchases.shared.repository.AgencyRepository;
import com.cafeoccidente.backend.users.entity.User;
import com.cafeoccidente.backend.users.repository.RoleRepository;
import com.cafeoccidente.backend.users.repository.UserRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.json.JsonMapper;

/**
 * HU-04 (RF-03) con JWT real. C8 (prueba del 11-nov, punto 6): todas las tablas del esquema como la hoja de datos de
 * Access, con busqueda, filtros, orden y carga por tramos en el servidor, de solo lectura y sin dejar entrar SQL del
 * cliente. Crea sus propias filas de empaques sobre la semilla de V2 (corre en CI). Transaccional: no deja nada.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TableAccessIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

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
        packaging(1L, "HU04-B1", 5, "2026-01-10");
        packaging(1L, "HU04-B2", 50, "2026-03-15");
        packaging(4L, "HU04-TAMBO", 7, "2026-02-01");
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

    private void packaging(Long agencyId, String transactionId, int entries, String date) {
        jdbc.update("INSERT INTO empaques (id_transaccion, agencia_id, tipo_empaque, fecha, cedula, entradas, salidas,"
                + " creado_en) VALUES (?, ?, 'NUEVO', CAST(? AS date), '123', ?, 0, now())", transactionId, agencyId, date, entries);
    }

    /** Una consulta como la hace la pantalla: columnas, filas como texto y total. */
    private record Sheet(List<String> columns, List<List<String>> rows, long total) {
        List<String> column(String name) {
            int index = columns.indexOf(name);
            return rows.stream().map(r -> r.get(index)).toList();
        }
    }

    private Sheet sheet(String auth, MockHttpServletRequestBuilder request) throws Exception {
        String body = mockMvc.perform(request.header("Authorization", auth)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JSON.readValue(body, Sheet.class);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> tables(String auth) throws Exception {
        String body = mockMvc.perform(get("/api/tables").header("Authorization", auth)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JSON.readValue(body, List.class);
    }

    private static List<String> names(List<Map<String, Object>> tables) {
        return tables.stream().map(t -> (String) t.get("name")).toList();
    }

    private static List<String> empaquesOf(Sheet sheet) {
        return sheet.column("id_transaccion").stream().filter(t -> t != null && t.startsWith("HU04-")).toList();
    }

    @Test
    void theAdministratorSeesEveryTableOfTheSchemaWithItsColumnsButNeverThePasswordHash() throws Exception {
        List<Map<String, Object>> tables = tables(admin);

        assertThat(names(tables)).contains("terceros", "usuarios", "empaques", "agencias", "historial_cambios", "permisos")
                .doesNotContain("flyway_schema_history");
        Map<String, Object> usuarios = tables.stream().filter(t -> "usuarios".equals(t.get("name"))).findFirst().orElseThrow();
        assertThat(usuarios.toString()).contains("usuario").doesNotContain("contrasena_hash");

        Sheet rows = sheet(admin, get("/api/tables/usuarios").param("agency", "ALL"));
        assertThat(rows.columns()).doesNotContain("contrasena_hash");
        String hash = jdbc.queryForObject("SELECT contrasena_hash FROM usuarios WHERE usuario = 'hu04_admin'", String.class);
        assertThat(rows.rows().toString()).doesNotContain(hash);
    }

    @Test
    void byDefaultTheAdministratorSeesTheWorkingAgencyAndCanAskForAnotherOrForAll() throws Exception {
        assertThat(empaquesOf(sheet(admin, get("/api/tables/empaques")))).containsExactlyInAnyOrder("HU04-B1", "HU04-B2");
        assertThat(empaquesOf(sheet(admin, get("/api/tables/empaques").param("agency", "4")))).containsExactly("HU04-TAMBO");
        assertThat(empaquesOf(sheet(admin, get("/api/tables/empaques").param("agency", "ALL"))))
                .containsExactlyInAnyOrder("HU04-B1", "HU04-B2", "HU04-TAMBO");
    }

    @Test
    void tablesWithoutAgencyAreShownComplete() throws Exception {
        long all = jdbc.queryForObject("SELECT count(*) FROM productos", Long.class);
        assertThat(sheet(admin, get("/api/tables/productos")).total()).isEqualTo(all);
    }

    @Test
    void aUserNeedsTheScreenAndOnlySeesTablesWithAgencyAndTheirOwnAgency() throws Exception {
        User clerk = user("hu04_usuario", Roles.USUARIO);
        mockMvc.perform(get("/api/tables/empaques").header("Authorization", token(clerk))).andExpect(status().isForbidden());

        jdbc.update("INSERT INTO permisos (usuario_id, pantalla, concedido_por) VALUES (?, ?, ?)",
                clerk.getId(), Screen.ACCESO_TABLAS.name(), clerk.getId());
        String auth = token(clerk);

        assertThat(names(tables(auth))).contains("empaques", "terceros")
                .doesNotContain("usuarios", "permisos", "historial_cambios", "productos", "agencias");
        // Pedir otra agencia o todas no sirve: siempre ve la suya.
        assertThat(empaquesOf(sheet(auth, get("/api/tables/empaques").param("agency", "ALL"))))
                .containsExactlyInAnyOrder("HU04-B1", "HU04-B2");
        assertThat(empaquesOf(sheet(auth, get("/api/tables/empaques").param("agency", "4")))).doesNotContain("HU04-TAMBO");
        mockMvc.perform(get("/api/tables/usuarios").header("Authorization", auth)).andExpect(status().isNotFound());
    }

    @Test
    void anUnknownTableIsNotFound() throws Exception {
        mockMvc.perform(get("/api/tables/flyway_schema_history").header("Authorization", admin)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/tables/empaques; DROP TABLE empaques").header("Authorization", admin))
                .andExpect(status().isNotFound());
    }

    @Test
    void searchFiltersAndSortRunInTheServerAndTheTotalFollows() throws Exception {
        Sheet search = sheet(admin, get("/api/tables/empaques").param("q", "hu04-b"));
        assertThat(empaquesOf(search)).containsExactlyInAnyOrder("HU04-B1", "HU04-B2");

        Sheet contains = sheet(admin, get("/api/tables/empaques").param("f.id_transaccion", "B2"));
        assertThat(contains.column("id_transaccion")).containsExactly("HU04-B2");
        assertThat(contains.total()).isEqualTo(1);

        Sheet range = sheet(admin, get("/api/tables/empaques").param("f.entradas.from", "10").param("f.entradas.to", "60")
                .param("f.id_transaccion", "HU04"));
        assertThat(range.column("id_transaccion")).containsExactly("HU04-B2");

        Sheet dates = sheet(admin, get("/api/tables/empaques").param("f.fecha.from", "2026-01-01").param("f.fecha.to", "2026-01-31")
                .param("f.id_transaccion", "HU04"));
        assertThat(dates.column("id_transaccion")).containsExactly("HU04-B1");

        Sheet asc = sheet(admin, get("/api/tables/empaques").param("f.id_transaccion", "HU04").param("sort", "entradas").param("dir", "asc"));
        assertThat(asc.column("id_transaccion")).containsExactly("HU04-B1", "HU04-B2");
        Sheet desc = sheet(admin, get("/api/tables/empaques").param("f.id_transaccion", "HU04").param("sort", "entradas").param("dir", "desc"));
        assertThat(desc.column("id_transaccion")).containsExactly("HU04-B2", "HU04-B1");
    }

    @Test
    void rowsComeInStretchesWithTheTotalOfTheWholeQuery() throws Exception {
        long total = jdbc.queryForObject("SELECT count(*) FROM empaques WHERE agencia_id = 1", Long.class);
        Sheet first = sheet(admin, get("/api/tables/empaques").param("limit", "1"));
        Sheet second = sheet(admin, get("/api/tables/empaques").param("limit", "1").param("offset", "1"));

        assertThat(first.rows()).hasSize(1);
        assertThat(first.total()).isEqualTo(total);
        assertThat(second.rows().get(0)).isNotEqualTo(first.rows().get(0));
    }

    @Test
    void sqlInTheFilterTheSearchOrTheSortIsTreatedAsDataOrRejected() throws Exception {
        long before = jdbc.queryForObject("SELECT count(*) FROM empaques", Long.class);

        assertThat(sheet(admin, get("/api/tables/empaques").param("f.id_transaccion", "' OR 1=1 --")).total()).isZero();
        assertThat(sheet(admin, get("/api/tables/empaques").param("q", "'; DROP TABLE empaques; --")).total()).isZero();
        assertThat(sheet(admin, get("/api/tables/empaques").param("q", "%")).total()).isZero();
        mockMvc.perform(get("/api/tables/empaques").param("sort", "id; DROP TABLE empaques").header("Authorization", admin))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/tables/empaques").param("dir", "asc; DROP TABLE empaques").header("Authorization", admin))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/tables/empaques").param("f.no_existe", "1").header("Authorization", admin))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/tables/empaques").param("f.entradas.from", "1 OR 1=1").header("Authorization", admin))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/tables/empaques").param("agency", "1 OR 1=1").header("Authorization", admin))
                .andExpect(status().isBadRequest());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM empaques", Long.class)).isEqualTo(before);
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
        List<MockHttpServletRequestBuilder> writes = new ArrayList<>(
                List.of(post("/api/tables/empaques"), put("/api/tables/empaques"), delete("/api/tables/empaques")));
        for (MockHttpServletRequestBuilder write : writes) {
            mockMvc.perform(write.header("Authorization", admin)).andExpect(status().isMethodNotAllowed());
        }
    }
}
