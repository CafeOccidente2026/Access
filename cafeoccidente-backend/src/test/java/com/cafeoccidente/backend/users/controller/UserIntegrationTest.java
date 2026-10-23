package com.cafeoccidente.backend.users.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.common.security.JwtService;
import com.cafeoccidente.backend.common.security.Roles;
import com.cafeoccidente.backend.common.security.SecurityUser;
import com.cafeoccidente.backend.purchases.shared.repository.AgencyRepository;
import com.cafeoccidente.backend.users.dto.UserUpdateRequest;
import com.cafeoccidente.backend.users.entity.User;
import com.cafeoccidente.backend.users.repository.RoleRepository;
import com.cafeoccidente.backend.users.repository.UserRepository;
import com.cafeoccidente.backend.users.service.UserService;
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
 * HU-08 (RF-07) y la edicion de usuarios (fase 3, C4 y C5) con JWT real. Crea sus propios usuarios
 * y terceros sobre la semilla de V2, asi que corre tambien en CI. Transaccional: no deja nada.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserIntegrationTest {

    private static final String NEW_ID = "990000801";

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
    private UserService userService;
    @Autowired
    private JdbcTemplate jdbc;

    private User admin;
    private String token;

    @BeforeEach
    void signIn() {
        admin = user("hu08_admin", Roles.ADMINISTRADOR, null);
        token = "Bearer " + jwtService.generateToken(new SecurityUser(admin));
    }

    private User user(String username, String role, String idNumber) {
        User user = new User();
        user.setUsername(username);
        user.setIdNumber(idNumber);
        user.setPasswordHash(passwordEncoder.encode("clave-hu08"));
        user.setRole(roles.findByName(role).orElseThrow());
        user.setAgency(agencies.findById(1L).orElseThrow());
        return users.save(user);
    }

    private long roleId(String role) {
        return roles.findByName(role).orElseThrow().getId();
    }

    private ResultActions create(String username, String idNumber, String password, long roleId) throws Exception {
        return mockMvc.perform(post("/api/users").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\": \"" + username + "\", \"idNumber\": \"" + idNumber + "\", \"password\": \""
                        + password + "\", \"roleId\": " + roleId + ", \"agencyId\": 1}"));
    }

    private ResultActions update(long id, String body) throws Exception {
        return mockMvc.perform(put("/api/users/" + id).header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String body(String role, long agencyId, boolean active, String password, String idNumber) {
        return "{\"roleId\": " + roleId(role) + ", \"agencyId\": " + agencyId + ", \"active\": " + active
                + ", \"password\": " + (password == null ? "null" : "\"" + password + "\"")
                + ", \"idNumber\": " + (idNumber == null ? "null" : "\"" + idNumber + "\"") + "}";
    }

    private Map<String, Object> grower(String idNumber) {
        return jdbc.queryForMap("SELECT primer_nombre, direccion, telefono, tipo, agencia_id FROM terceros"
                + " WHERE identificacion = ?", idNumber);
    }

    @Test
    void ca1TheUserIsSavedInUsersAndInTerceros() throws Exception {
        create("hu08_nuevo", NEW_ID, "ab12", roleId(Roles.OPERACIONES))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idNumber").value(NEW_ID))
                .andExpect(jsonPath("$.roleName").value(Roles.OPERACIONES));
        assertThat(jdbc.queryForObject("SELECT cedula FROM usuarios WHERE usuario = 'hu08_nuevo'", String.class))
                .isEqualTo(NEW_ID);
        assertThat(grower(NEW_ID)).containsEntry("primer_nombre", "hu08_nuevo").containsEntry("direccion", "-")
                .containsEntry("telefono", "-").containsEntry("tipo", "C").containsEntry("agencia_id", 1L);
    }

    @Test
    void ca1AnExistingTerceroIsReusedWithoutChanges() throws Exception {
        jdbc.update("INSERT INTO terceros (identificacion, primer_nombre, direccion, telefono, tipo, agencia_id)"
                + " VALUES (?, 'ASOCIADO PRUEBA', 'VEREDA X', '3001234567', 'S', 4)", NEW_ID);
        Map<String, Object> before = grower(NEW_ID);

        create("hu08_asociado", NEW_ID, "ab12", roleId(Roles.USUARIO)).andExpect(status().isCreated());

        assertThat(grower(NEW_ID)).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM terceros WHERE identificacion = ?", Long.class, NEW_ID))
                .isEqualTo(1L);
    }

    @Test
    void ca2InsecurePasswordsAreRejected() throws Exception {
        String[] weak = {"abc", "abcd", "1234", "ab1"};
        for (int i = 0; i < weak.length; i++) {
            create("hu08_debil" + i, "99000081" + i, weak[i], roleId(Roles.USUARIO)).andExpect(status().isBadRequest());
        }
        create("hu08_segura", "990000819", "ab12", roleId(Roles.USUARIO)).andExpect(status().isCreated());
    }

    @Test
    void theIdNumberIsNumericAndUniqueAndTheRoleMustExist() throws Exception {
        create("hu08_letras", "12a45", "ab12", roleId(Roles.USUARIO)).andExpect(status().isBadRequest());
        create("hu08_uno", NEW_ID, "ab12", roleId(Roles.USUARIO)).andExpect(status().isCreated());
        create("hu08_dos", NEW_ID, "ab12", roleId(Roles.USUARIO))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Ya existe un usuario con esa cedula"));
        create("hu08_sinrol", "990000802", "ab12", 999).andExpect(status().isNotFound());
    }

    @Test
    void c4AnAdministratorCannotDeactivateHimself() throws Exception {
        update(admin.getId(), body(Roles.ADMINISTRADOR, 1, false, null, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("No puede desactivarse ni quitarse el rol de Administrador a si mismo"));
    }

    @Test
    void c4AnAdministratorCannotRemoveHisOwnRole() throws Exception {
        update(admin.getId(), body(Roles.USUARIO, 1, true, null, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("No puede desactivarse ni quitarse el rol de Administrador a si mismo"));
    }

    @Test
    void c4TheLastActiveAdministratorCannotBeDeactivatedNorDemoted() {
        User last = user("hu08_ultimo", Roles.ADMINISTRADOR, null);
        jdbc.update("UPDATE usuarios SET activo = false WHERE id <> ? AND rol_id = ?", last.getId(), roleId(Roles.ADMINISTRADOR));
        long other = user("hu08_otro", Roles.USUARIO, null).getId();

        for (UserUpdateRequest change : new UserUpdateRequest[] {
                new UserUpdateRequest(roleId(Roles.ADMINISTRADOR), 1L, false, null, null),
                new UserUpdateRequest(roleId(Roles.USUARIO), 1L, true, null, null)}) {
            assertThatThrownBy(() -> userService.update(last.getId(), change, other))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("Debe quedar al menos un Administrador activo");
        }
    }

    @Test
    void c4TheAdministratorReassignsAgencyRoleActiveAndPassword() throws Exception {
        User target = user("hu08_reasignado", Roles.USUARIO, "990000803");
        update(target.getId(), body(Roles.OPERACIONES, 4, false, "nueva1", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agencyName").value("El Tambo"))
                .andExpect(jsonPath("$.roleName").value(Roles.OPERACIONES))
                .andExpect(jsonPath("$.active").value(false));
        User saved = users.findById(target.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("nueva1", saved.getPasswordHash())).isTrue();
        update(target.getId(), body(Roles.USUARIO, 1, true, "debil", null)).andExpect(status().isBadRequest());
    }

    @Test
    void anExistingUserWithoutIdNumberGetsOneAndItsTercero() throws Exception {
        User old = user("hu08_antiguo", Roles.USUARIO, null);
        update(old.getId(), body(Roles.USUARIO, 1, true, null, NEW_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idNumber").value(NEW_ID));
        assertThat(grower(NEW_ID)).containsEntry("tipo", "C").containsEntry("primer_nombre", "hu08_antiguo");

        update(old.getId(), body(Roles.USUARIO, 1, true, null, "990000804"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La cedula del usuario no se puede cambiar"));
    }
}
