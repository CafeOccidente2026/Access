package com.cafeoccidente.backend.purchases.future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cafeoccidente.backend.common.security.JwtService;
import com.cafeoccidente.backend.common.security.Roles;
import com.cafeoccidente.backend.common.security.SecurityUser;
import com.cafeoccidente.backend.purchases.future.dto.AnnouncementResponse;
import com.cafeoccidente.backend.purchases.future.service.AnnouncementService;
import com.cafeoccidente.backend.purchases.shared.repository.AgencyRepository;
import com.cafeoccidente.backend.users.entity.User;
import com.cafeoccidente.backend.users.repository.RoleRepository;
import com.cafeoccidente.backend.users.repository.UserRepository;
import java.math.BigDecimal;
import java.util.List;
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
 * HU-10 y HU-11 (RF-08) con JWT real sobre la semilla de V2: 25 productos en RP (24 sin PASILLA), y Buesaco 1
 * y El Tambo con Registro de Control (Base Carga 125). Transaccional: no deja nada. Corre tambien en CI.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AnnouncementIntegrationTest {

    private static final String RP_TYPES = "SELECT count(DISTINCT especial) FROM productos p JOIN fondos f ON f.id = p.fondo_id"
            + " WHERE f.codigo = 'RP' AND especial <> 'PASILLA'";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AnnouncementService announcementService;
    @Autowired
    private JdbcTemplate jdbc;
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

    private Long rp;
    private String operations;

    @BeforeEach
    void setUp() {
        rp = jdbc.queryForObject("SELECT id FROM fondos WHERE codigo = 'RP'", Long.class);
        User user = new User();
        user.setUsername("hu10_operaciones");
        user.setPasswordHash(passwordEncoder.encode("clave-hu10"));
        user.setRole(roles.findByName(Roles.OPERACIONES).orElseThrow());
        user.setAgency(agencies.findById(1L).orElseThrow());
        operations = "Bearer " + jwtService.generateToken(new SecurityUser(users.save(user)));
    }

    /** Un anuncio maestro de RP con su numero en Buesaco 1, como lo deja una publicacion anterior. */
    private void previous(String specialType, String surcharge, String pasilla) {
        Long id = jdbc.queryForObject("INSERT INTO anuncios (fecha_anuncio, pr_base_cps, pr_alm_defec, sobrepr_carga_esp,"
                + " pr_inc_calidad, fondo_id, especial, activo) VALUES (CURRENT_DATE - 1, 1400000, ?::numeric, ?::numeric, 50,"
                + " ?, ?, true) RETURNING id", Long.class, pasilla, surcharge, rp, specialType);
        jdbc.update("INSERT INTO anuncios_agencias (agencia_id, anuncio_id, numero_anuncio, fecha_asignacion)"
                + " VALUES (1, ?, ?, CURRENT_DATE - 1)", id, maxNumber(1) + 1);
    }

    private int maxNumber(long agencyId) {
        return jdbc.queryForObject("SELECT COALESCE(MAX(numero_anuncio), 0) FROM anuncios_agencias WHERE agencia_id = ?",
                Integer.class, agencyId);
    }

    private ResultActions publishAll(String basePriceLoad) throws Exception {
        return mockMvc.perform(post("/api/announcements").header("Authorization", operations)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fundId\": " + rp + ", \"basePriceLoad\": " + basePriceLoad + "}"));
    }

    private ResultActions publishType(String body) throws Exception {
        return mockMvc.perform(post("/api/announcements/types").header("Authorization", operations)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void hu10Ca1OnePriceForEveryTypeKeepingItsBonusAndPasillaPrice() throws Exception {
        previous("RN", "80000", "6000");
        int types = jdbc.queryForObject(RP_TYPES, Integer.class);

        publishAll("1546900")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(types));

        AnnouncementResponse rn = announcementService.findLatest(1L, rp, "RN");
        assertThat(rn.basePriceLoad()).isEqualByComparingTo("1546900");
        assertThat(rn.bonus()).isEqualByComparingTo("640"); // 80.000 / Base Carga 125
        assertThat(rn.defectiveUnitPrice()).isEqualByComparingTo("6000");
        assertThat(rn.qualityIncrement()).isEqualByComparingTo("50");

        // Un tipo sin anuncio anterior sale con bonificacion y pasilla en 0 (C5).
        AnnouncementResponse illy = announcementService.findLatest(1L, rp, "ILLY");
        assertThat(illy.basePriceLoad()).isEqualByComparingTo("1546900");
        assertThat(illy.bonus()).isZero();
        assertThat(illy.defectiveUnitPrice()).isZero();
        // PASILLA no se publica desde aqui (RF-09).
        assertThat(jdbc.queryForObject("SELECT count(*) FROM anuncios WHERE especial = 'PASILLA' AND pr_base_cps = 1546900",
                Integer.class)).isZero();
    }

    @Test
    void hu10Ca3EachAgencyGetsOneNumberPerTypeAndTheAnnouncementIsAvailableToAll() throws Exception {
        int types = jdbc.queryForObject(RP_TYPES, Integer.class);
        int buesaco = maxNumber(1);
        int tambo = maxNumber(4);

        publishAll("1546900").andExpect(status().isCreated());

        assertThat(maxNumber(1)).isEqualTo(buesaco + types);
        assertThat(maxNumber(4)).isEqualTo(tambo + types);
        assertThat(announcementService.findLatest(4L, rp, "CV").basePriceLoad()).isEqualByComparingTo("1546900");
    }

    @Test
    void eachPublishedTypeIsInTheHistory() throws Exception {
        int types = jdbc.queryForObject(RP_TYPES, Integer.class);
        publishAll("1546900").andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM historial_cambios WHERE entidad = 'ANUNCIO' AND accion = 'PUBLICAR'"
                + " AND despues->>'basePriceLoad' = '1546900'", Integer.class)).isEqualTo(types);
    }

    @Test
    void hu11Ca1TheListShowsEachTypeWithItsCurrentAnnouncement() throws Exception {
        previous("RN", "80000", "6000");
        mockMvc.perform(get("/api/announcements/types?fundId=" + rp).header("Authorization", operations))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(jdbc.queryForObject(RP_TYPES, Integer.class)))
                .andExpect(jsonPath("$[?(@.specialType == 'RN')].specialSurcharge").value(80000.0))
                .andExpect(jsonPath("$[?(@.specialType == 'RN')].defectiveUnitPrice").value(6000.0))
                .andExpect(jsonPath("$[?(@.specialType == 'ILLY')].basePriceLoad").value(org.hamcrest.Matchers.contains(
                        org.hamcrest.Matchers.nullValue())));
    }

    @Test
    void hu11Ca2OnlyTheChosenTypeChangesAndWithoutPriceItKeepsTheCurrentOne() throws Exception {
        publishAll("1546900").andExpect(status().isCreated());
        int before = maxNumber(1);
        BigDecimal cvBefore = announcementService.findLatest(1L, rp, "CV").bonus();

        publishType("{\"fundId\": " + rp + ", \"specialType\": \"RN\", \"specialSurcharge\": 100000, \"defectiveUnitPrice\": 7000}")
                .andExpect(status().isCreated());

        AnnouncementResponse rn = announcementService.findLatest(1L, rp, "RN");
        assertThat(rn.basePriceLoad()).isEqualByComparingTo("1546900"); // el precio del dia
        assertThat(rn.bonus()).isEqualByComparingTo("800"); // 100.000 / 125
        assertThat(rn.defectiveUnitPrice()).isEqualByComparingTo("7000");
        assertThat(maxNumber(1)).isEqualTo(before + 1);
        assertThat(announcementService.findLatest(1L, rp, "CV").bonus()).isEqualByComparingTo(cvBefore);

        publishType("{\"fundId\": " + rp + ", \"specialType\": \"RN\", \"basePriceLoad\": 1600000,"
                + " \"specialSurcharge\": 100000, \"defectiveUnitPrice\": 7000}").andExpect(status().isCreated());
        assertThat(announcementService.findLatest(1L, rp, "RN").basePriceLoad()).isEqualByComparingTo("1600000");
    }

    @Test
    void pasillaAndForeignTypesAreRejected() throws Exception {
        publishType("{\"fundId\": " + rp + ", \"specialType\": \"PASILLA\", \"basePriceLoad\": 1, \"specialSurcharge\": 0,"
                + " \"defectiveUnitPrice\": 0}").andExpect(status().isBadRequest());
        publishType("{\"fundId\": " + rp + ", \"specialType\": \"NO EXISTE\", \"basePriceLoad\": 1, \"specialSurcharge\": 0,"
                + " \"defectiveUnitPrice\": 0}").andExpect(status().isBadRequest());
        // Un tipo sin anuncio necesita precio.
        publishType("{\"fundId\": " + rp + ", \"specialType\": \"ILLY\", \"specialSurcharge\": 0, \"defectiveUnitPrice\": 0}")
                .andExpect(status().isBadRequest());
        publishAll("0").andExpect(status().isBadRequest());
    }

    @Test
    void hu12Ca1ThePasillaAnnouncementIsAvailableToEveryAgency() throws Exception {
        int buesaco = maxNumber(1);
        int tambo = maxNumber(4);

        mockMvc.perform(post("/api/announcements/husk").header("Authorization", operations)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"basePriceLoad\": 1200000, \"pointPrice\": 9600}"))
                .andExpect(status().isCreated());

        // RF-09: Fondo RP y Especial PASILLA fijos; Pr Por Punto es el precio que usa la compra de pasilla.
        for (long agency : new long[] {1, 4}) {
            AnnouncementResponse pasilla = announcementService.findLatest(agency, rp, "PASILLA");
            assertThat(pasilla.basePriceLoad()).isEqualByComparingTo("1200000");
            assertThat(pasilla.healthyUnitPrice()).isEqualByComparingTo("9600");
        }
        assertThat(maxNumber(1)).isEqualTo(buesaco + 1);
        assertThat(maxNumber(4)).isEqualTo(tambo + 1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM historial_cambios WHERE entidad = 'ANUNCIO'"
                + " AND despues->>'specialType' = 'PASILLA' AND despues->>'pointPrice' = '9600'", Integer.class)).isEqualTo(1);
    }

    @Test
    void theBonusIsTheSurchargePerLoadOverTheBaseLoadOfTheAgency() {
        // RF-08: SobrePr Carga Esp 80.000; Base Carga de Buesaco 1 = 125 -> Bonificacion 640 por kilo.
        previous("RN", "80000", "6000");
        assertThat(announcementService.findLatest(1L, rp, "RN").bonus()).isEqualByComparingTo("640");
    }

    @Test
    void d5TheObsoleteColumnsOfAnnouncementsAreGone() {
        List<String> columns = jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = 'anuncios'", String.class);
        assertThat(columns)
                .doesNotContain("numero_anuncio", "agencia_id", "pr_alm_sana", "bonificacion", "costos")
                .contains("sobrepr_carga_esp", "pr_alm_defec", "pr_base_cps");
    }
}
