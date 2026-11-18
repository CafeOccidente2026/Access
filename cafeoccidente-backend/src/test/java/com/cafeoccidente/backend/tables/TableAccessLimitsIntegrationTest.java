package com.cafeoccidente.backend.tables;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cafeoccidente.backend.common.security.JwtService;
import com.cafeoccidente.backend.common.security.Roles;
import com.cafeoccidente.backend.common.security.SecurityUser;
import com.cafeoccidente.backend.purchases.shared.repository.AgencyRepository;
import com.cafeoccidente.backend.users.entity.User;
import com.cafeoccidente.backend.users.repository.RoleRepository;
import com.cafeoccidente.backend.users.repository.UserRepository;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * S3 (seguridad): la consulta de Acceso a Tablas corre en una transaccion de solo lectura con un tope de 5 s
 * (SET LOCAL solo vale dentro de una transaccion). Sin @Transactional en el test: si lo tuviera, la consulta se uniria
 * a la transaccion del test y no se veria ni el solo lectura ni el tope. El usuario se borra al final.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TableAccessLimitsIntegrationTest {

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
    private DataSource dataSource;
    @MockitoSpyBean
    private NamedParameterJdbcTemplate jdbc;

    private User admin;
    private String token;

    @BeforeEach
    void createAdmin() {
        User user = new User();
        user.setUsername("s3_admin");
        user.setPasswordHash(passwordEncoder.encode("clave-s3"));
        user.setRole(roles.findByName(Roles.ADMINISTRADOR).orElseThrow());
        user.setAgency(agencies.findById(1L).orElseThrow());
        admin = users.save(user);
        token = "Bearer " + jwtService.generateToken(new SecurityUser(admin));
    }

    @AfterEach
    void deleteAdmin() {
        users.deleteById(admin.getId());
    }

    @Test
    void theQueryRunsInAReadOnlyTransactionWithAFiveSecondLimit() throws Exception {
        AtomicReference<String> readOnly = new AtomicReference<>();
        AtomicReference<String> timeout = new AtomicReference<>();
        // Lo que ve la base en la misma conexion, justo antes de contar las filas.
        doAnswer(call -> {
            readOnly.set(jdbc.getJdbcTemplate().queryForObject("SHOW transaction_read_only", String.class));
            timeout.set(jdbc.getJdbcTemplate().queryForObject("SHOW statement_timeout", String.class));
            return call.callRealMethod();
        }).when(jdbc).queryForObject(startsWith("SELECT count(*)"), any(MapSqlParameterSource.class), eq(Long.class));

        mockMvc.perform(get("/api/tables/fondos").header("Authorization", token)).andExpect(status().isOk());

        assertThat(readOnly.get()).isEqualTo("on");
        assertThat(timeout.get()).isEqualTo("5s");
    }

    @Test
    void aQueryThatWaitsMoreThanFiveSecondsIsCutWithAClearMessage() throws Exception {
        try (Connection other = dataSource.getConnection(); Statement statement = other.createStatement()) {
            other.setAutoCommit(false);
            // Otra conexion bloquea la tabla: la hoja tendria que esperar sin fin.
            statement.execute("LOCK TABLE fondos IN ACCESS EXCLUSIVE MODE");
            long start = System.nanoTime();
            try {
                mockMvc.perform(get("/api/tables/fondos").header("Authorization", token))
                        .andExpect(status().isServiceUnavailable())
                        .andExpect(jsonPath("$.message").value(Matchers.containsString("5 s")));
            } finally {
                other.rollback();
            }
            Duration waited = Duration.ofNanos(System.nanoTime() - start);
            assertThat(waited).isBetween(Duration.ofMillis(4500), Duration.ofSeconds(10));
        }
    }
}
