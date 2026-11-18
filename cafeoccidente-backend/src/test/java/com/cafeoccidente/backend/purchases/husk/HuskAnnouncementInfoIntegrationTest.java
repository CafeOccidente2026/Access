package com.cafeoccidente.backend.purchases.husk;

import static org.assertj.core.api.Assertions.assertThat;

import com.cafeoccidente.backend.common.security.Roles;
import com.cafeoccidente.backend.common.security.SecurityUser;
import com.cafeoccidente.backend.controlrecord.service.ControlRecordService;
import com.cafeoccidente.backend.purchases.husk.service.HuskPurchaseService;
import com.cafeoccidente.backend.purchases.shared.repository.AgencyRepository;
import com.cafeoccidente.backend.users.entity.User;
import com.cafeoccidente.backend.users.repository.RoleRepository;
import com.cafeoccidente.backend.users.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

/** Hallazgo #10 (16-nov): la pantalla de Pasilla recibe el tamano de la muestra para validar Peso Almendra al momento. */
@SpringBootTest
@Transactional
@SuppressWarnings("null")
class HuskAnnouncementInfoIntegrationTest {

    @Autowired
    private HuskPurchaseService husk;
    @Autowired
    private ControlRecordService controlRecords;
    @Autowired
    private UserRepository users;
    @Autowired
    private RoleRepository roles;
    @Autowired
    private AgencyRepository agencies;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void signIn() {
        User user = new User();
        user.setUsername("prueba_pasilla");
        user.setPasswordHash("x");
        user.setRole(roles.findByName(Roles.USUARIO).orElseThrow());
        user.setAgency(agencies.findById(1L).orElseThrow());
        SecurityUser principal = new SecurityUser(users.save(user));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        Long rp = jdbc.queryForObject("SELECT id FROM fondos WHERE codigo = 'RP'", Long.class);
        Long id = jdbc.queryForObject("INSERT INTO anuncios (fecha_anuncio, pr_base_cps, pr_alm_defec, sobrepr_carga_esp,"
                + " pr_inc_calidad, fondo_id, especial, activo) VALUES (CURRENT_DATE, 1400000, 9000, 0, 0, ?, 'PASILLA', true)"
                + " RETURNING id", Long.class, rp);
        jdbc.update("INSERT INTO anuncios_agencias (agencia_id, anuncio_id, numero_anuncio, fecha_asignacion)"
                + " VALUES (1, ?, (SELECT COALESCE(MAX(numero_anuncio), 0) + 1 FROM anuncios_agencias WHERE agencia_id = 1),"
                + " CURRENT_DATE)", id);
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void theAnnouncementInfoBringsTheSampleSizeOfTheAgency() {
        assertThat(husk.announcementInfo().sampleSize())
                .isEqualByComparingTo(controlRecords.getActive(1L).getSampleSize());
    }
}
