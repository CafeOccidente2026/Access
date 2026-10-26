package com.cafeoccidente.backend.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cafeoccidente.backend.common.config.AdminUserSeeder;
import com.cafeoccidente.backend.common.security.JwtService;
import com.cafeoccidente.backend.common.security.Roles;
import com.cafeoccidente.backend.common.security.SecurityUser;
import com.cafeoccidente.backend.history.service.ChangeHistoryService;
import com.cafeoccidente.backend.purchases.shared.repository.AgencyRepository;
import com.cafeoccidente.backend.users.entity.User;
import com.cafeoccidente.backend.users.repository.RoleRepository;
import com.cafeoccidente.backend.users.repository.UserRepository;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Historial de cambios (fase 3c) con JWT real sobre la semilla de V2: cada punto que escribe deja su
 * fila con quien, entidad, accion, antes y despues, y nunca la contrasena. Corre tambien en CI.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ChangeHistoryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JsonMapper json;
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
    private ChangeHistoryService history;
    @Autowired
    private JdbcTemplate jdbc;

    private User admin;
    private String token;

    @BeforeEach
    void signIn() {
        admin = new User();
        admin.setUsername("hist_admin");
        admin.setPasswordHash(passwordEncoder.encode("clave-hist1"));
        admin.setRole(roles.findByName(Roles.ADMINISTRADOR).orElseThrow());
        admin.setAgency(agencies.findById(1L).orElseThrow());
        admin = users.save(admin);
        token = "Bearer " + jwtService.generateToken(new SecurityUser(admin));
    }

    private ResultActions send(MockHttpServletRequestBuilder request,
            String body) throws Exception {
        return mockMvc.perform(request.header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    /** La ultima fila de la entidad: usuario, accion, agencia, antes y despues (como texto JSON). */
    private Map<String, Object> last(String entity) {
        return jdbc.queryForMap("SELECT usuario_id, accion, agencia_id, entidad_id, antes::text AS antes,"
                + " despues::text AS despues FROM historial_cambios WHERE entidad = ? ORDER BY id DESC LIMIT 1", entity);
    }

    private long rows() {
        return jdbc.queryForObject("SELECT count(*) FROM historial_cambios", Long.class);
    }

    private String userBody(String role, long agencyId, boolean active, String password) {
        return "{\"roleId\": " + roles.findByName(role).orElseThrow().getId() + ", \"agencyId\": " + agencyId
                + ", \"active\": " + active + ", \"password\": " + (password == null ? "null" : "\"" + password + "\"")
                + ", \"idNumber\": null}";
    }

    @Test
    void controlRecordChangesKeepOnlyTheChangedFieldsBeforeAndAfter() throws Exception {
        String current = mockMvc.perform(get("/api/control-records/1").header("Authorization", token))
                .andReturn().getResponse().getContentAsString();
        ObjectNode body = (ObjectNode) json.readTree(current);
        send(put("/api/control-records/1"), body.put("baseLoad", 126).toString()).andExpect(status().isOk());

        Map<String, Object> row = last("REGISTRO_CONTROL");
        assertThat(row).containsEntry("usuario_id", admin.getId()).containsEntry("accion", "MODIFICAR")
                .containsEntry("agencia_id", 1L);
        assertThat(json.readTree((String) row.get("antes")).toString()).isEqualTo("{\"baseLoad\":\"125\"}");
        assertThat(json.readTree((String) row.get("despues")).toString()).isEqualTo("{\"baseLoad\":\"126\"}");
    }

    @Test
    void publishingAnAnnouncementIsRecordedWithoutAgency() throws Exception {
        // RF-08: un precio para todos los tipos; cada tipo publicado deja su fila.
        send(post("/api/announcements"), "{\"basePriceLoad\": 1500000, \"fundId\": 1}")
                .andExpect(status().isCreated());
        Map<String, Object> row = last("ANUNCIO");
        assertThat(row).containsEntry("accion", "PUBLICAR").containsEntry("agencia_id", null);
        assertThat((String) row.get("despues")).contains("\"specialType\": ").contains("\"fund\": \"RP\"")
                .contains("\"basePriceLoad\": \"1500000\"");
        assertThat(row.get("antes")).isNull();
    }

    @Test
    void newUsersReassignmentsAndDeactivationsAreRecordedWithoutThePassword() throws Exception {
        send(post("/api/users"), "{\"username\": \"hist_nuevo\", \"idNumber\": \"990000901\", \"password\": \"clave1\","
                + " \"roleId\": " + roles.findByName(Roles.USUARIO).orElseThrow().getId() + ", \"agencyId\": 1}")
                .andExpect(status().isCreated());
        Map<String, Object> created = last("USUARIO");
        assertThat(created).containsEntry("accion", "CREAR");
        assertThat((String) created.get("despues")).contains("hist_nuevo").contains("USUARIO").doesNotContain("clave1");
        long id = (Long) created.get("entidad_id");

        send(put("/api/users/" + id), userBody(Roles.USUARIO, 4, true, null)).andExpect(status().isOk());
        Map<String, Object> reassigned = last("USUARIO");
        assertThat(reassigned).containsEntry("accion", "REASIGNAR").containsEntry("agencia_id", 4L);
        assertThat((String) reassigned.get("antes")).contains("Buesaco");
        assertThat((String) reassigned.get("despues")).contains("El Tambo");

        send(put("/api/users/" + id), userBody(Roles.OPERACIONES, 4, true, null)).andExpect(status().isOk());
        assertThat(last("USUARIO")).containsEntry("accion", "MODIFICAR");

        send(put("/api/users/" + id), userBody(Roles.OPERACIONES, 4, false, "secreta9")).andExpect(status().isOk());
        Map<String, Object> deactivated = last("USUARIO");
        assertThat(deactivated).containsEntry("accion", "DESACTIVAR");
        String after = (String) deactivated.get("despues");
        assertThat(after).contains("\"password\": \"cambiada\"").doesNotContain("secreta9");
        String hash = jdbc.queryForObject("SELECT contrasena_hash FROM usuarios WHERE id = ?", String.class, id);
        assertThat(after).doesNotContain(hash);

        send(put("/api/users/" + id), userBody(Roles.OPERACIONES, 4, true, null)).andExpect(status().isOk());
        assertThat(last("USUARIO")).containsEntry("accion", "ACTIVAR");
    }

    @Test
    void creatingAnAgencyIsRecorded() throws Exception {
        send(post("/api/agencies"), "{\"name\": \"Agencia Historial\"}").andExpect(status().isCreated());
        Map<String, Object> row = last("AGENCIA");
        assertThat(row).containsEntry("accion", "CREAR");
        assertThat((String) row.get("despues")).contains("Agencia Historial");
    }

    @Test
    void aRejectedChangeLeavesNoRowAndAnUnchangedSaveNeitherDoes() throws Exception {
        long before = rows();
        send(put("/api/users/" + admin.getId()), userBody(Roles.ADMINISTRADOR, 1, false, null))
                .andExpect(status().isBadRequest());
        send(put("/api/users/" + admin.getId()), userBody(Roles.ADMINISTRADOR, 1, true, null))
                .andExpect(status().isOk());
        assertThat(rows()).isEqualTo(before);
    }

    @Test
    void systemTasksWithoutSessionRecordNothingAndDoNotFail() throws Exception {
        SecurityContextHolder.clearContext();
        long before = rows();
        new AdminUserSeeder(users, roles, agencies, passwordEncoder, "hist_seed", "clave-seed1")
                .run(new DefaultApplicationArguments());
        history.record(ChangeHistoryService.Entity.AGENCIA, 1L, ChangeHistoryService.Action.CREAR, 1L, null,
                Map.of("name", "sin sesion"));
        assertThat(users.findByUsername("hist_seed")).isPresent();
        assertThat(rows()).isEqualTo(before);
    }

    @Test
    void theAdministratorFiltersTheHistory() throws Exception {
        send(post("/api/agencies"), "{\"name\": \"Agencia Filtro\"}").andExpect(status().isCreated());
        mockMvc.perform(get("/api/history").param("entity", "AGENCIA").param("userId", admin.getId().toString())
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].username").value("hist_admin"))
                .andExpect(jsonPath("$.items[0].action").value("CREAR"))
                .andExpect(jsonPath("$.items[0].after.name").value("Agencia Filtro"));
        mockMvc.perform(get("/api/history").param("entity", "AGENCIA").param("userId", admin.getId().toString())
                        .param("from", "2000-01-01").param("to", "2000-01-02").header("Authorization", token))
                .andExpect(jsonPath("$.total").value(0));
    }
}
