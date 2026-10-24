package com.cafeoccidente.backend.controlrecord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cafeoccidente.backend.common.security.JwtService;
import com.cafeoccidente.backend.common.security.Roles;
import com.cafeoccidente.backend.common.security.SecurityUser;
import com.cafeoccidente.backend.controlrecord.service.ControlRecordService;
import com.cafeoccidente.backend.purchases.drycoffee.service.DryCoffeePurchaseCalculator;
import com.cafeoccidente.backend.purchases.shared.repository.AgencyRepository;
import com.cafeoccidente.backend.users.entity.User;
import com.cafeoccidente.backend.users.repository.RoleRepository;
import com.cafeoccidente.backend.users.repository.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * HU-07 (RF-06) con JWT real sobre el Registro de Control de Buesaco de la semilla, asi que corre
 * tambien en CI. Transaccional: no deja nada.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ControlRecordIntegrationTest {

    private static final String BUESACO = "/api/control-records/1";

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
    private ControlRecordService service;
    @Autowired
    private JdbcTemplate jdbc;

    private String admin;

    @BeforeEach
    void signIn() {
        User user = new User();
        user.setUsername("hu07_admin");
        user.setPasswordHash(passwordEncoder.encode("clave-hu07"));
        user.setRole(roles.findByName(Roles.ADMINISTRADOR).orElseThrow());
        user.setAgency(agencies.findById(1L).orElseThrow());
        admin = "Bearer " + jwtService.generateToken(new SecurityUser(users.save(user)));
    }

    /** El registro actual, listo para mandarlo de vuelta con algun cambio. */
    private ObjectNode current() throws Exception {
        String body = mockMvc.perform(get(BUESACO).header("Authorization", admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return (ObjectNode) json.readTree(body);
    }

    private ResultActions save(ObjectNode body) throws Exception {
        return mockMvc.perform(put(BUESACO).header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(body.toString()));
    }

    @Test
    void ca1ChoosingTheAgencyFillsThePurchasePointAndLoadsItsParameters() throws Exception {
        mockMvc.perform(get(BUESACO).header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agencyName").value("Buesaco 1"))
                .andExpect(jsonPath("$.purchasePoint").value("BUESACO1 OCCIDENTE"))
                .andExpect(jsonPath("$.excelsoKg").value(70))
                .andExpect(jsonPath("$.baseFactor").value(94.0));
    }

    @Test
    void aSecondControlRecordForTheSameAgencyIsRejected() throws Exception {
        mockMvc.perform(post(BUESACO).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(current().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Esta agencia ya tiene un Registro de Control activo; use editar en vez de crear uno nuevo"));
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM registros_control WHERE agencia_id = 1 AND activo", Long.class)).isEqualTo(1L);
    }

    @Test
    void theClientCannotChangeThePurchasePoint() throws Exception {
        save(current().put("purchasePoint", "OTRO PUNTO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purchasePoint").value("BUESACO1 OCCIDENTE"));
    }

    @Test
    void ca2SavedValuesApplyRightAwayToTheNextCalculations() throws Exception {
        DryCoffeePurchaseCalculator calculator = new DryCoffeePurchaseCalculator();
        BigDecimal healthyWeight = new BigDecimal("186.17");
        BigDecimal before = calculator.healthyPercentage(healthyWeight, service.getActive(1L));

        save(current().put("excelsoKg", 72)).andExpect(status().isOk());

        BigDecimal after = calculator.healthyPercentage(healthyWeight, service.getActive(1L));
        // PorcAlmSana = TamMuestra x KgExcelso / Peso Alm Sana: con 72 en vez de 70 sube en la misma proporcion.
        assertThat(after).isEqualByComparingTo(before.multiply(new BigDecimal("72"))
                .divide(new BigDecimal("70"), 2, RoundingMode.HALF_UP));
    }

    @Test
    void ca3NumericFieldsRejectLetters() throws Exception {
        save(current().put("baseLoad", "abc")).andExpect(status().isBadRequest());
        save(current().put("baseFactor", "9x")).andExpect(status().isBadRequest());
        save(current().put("controlNumber", "dos")).andExpect(status().isBadRequest());
    }

    @Test
    void theResolutionDateIsKeptAsTyped() throws Exception {
        save(current().put("resolutionDate", "2025-01-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolutionDate").value("2025-01-15"));
    }

    @Test
    void greenCoffeeValuesAndThresholdAreSaved() throws Exception {
        save(current().put("greenTotalStoredWeight", 140).put("greenHealthyUnitPrice", 1000)
                        .put("specialtyThreshold", 93.5))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.greenTotalStoredWeight").value(140.0))
                .andExpect(jsonPath("$.greenHealthyUnitPrice").value(1000.0))
                .andExpect(jsonPath("$.specialtyThreshold").value(93.5));
    }
}
