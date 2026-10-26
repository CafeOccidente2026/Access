package com.cafeoccidente.backend.purchases.future;

import static org.assertj.core.api.Assertions.assertThat;

import com.cafeoccidente.backend.purchases.future.dto.AnnouncementResponse;
import com.cafeoccidente.backend.purchases.future.service.AnnouncementService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Anuncios (RF-08) sobre la semilla de V2: Buesaco 1 tiene Registro de Control con Base Carga 125.
 * Transaccional: no deja nada. Corre tambien en CI.
 */
@SpringBootTest
@Transactional
class AnnouncementIntegrationTest {

    private static final String SPECIAL = "PRUEBA FASE 5";

    @Autowired
    private AnnouncementService announcementService;
    @Autowired
    private JdbcTemplate jdbc;

    private Long fundRp() {
        return jdbc.queryForObject("SELECT id FROM fondos WHERE codigo = 'RP'", Long.class);
    }

    /** Un anuncio maestro con su numero en Buesaco 1, como lo deja la publicacion. */
    private void publish(String specialSurcharge) {
        Long id = jdbc.queryForObject("INSERT INTO anuncios (fecha_anuncio, pr_base_cps, pr_alm_defec, sobrepr_carga_esp,"
                + " fondo_id, especial, activo) VALUES (CURRENT_DATE, 1546900, 6000, ?::numeric, ?, ?, true) RETURNING id",
                Long.class, specialSurcharge, fundRp(), SPECIAL);
        Integer next = jdbc.queryForObject(
                "SELECT COALESCE(MAX(numero_anuncio), 0) + 1 FROM anuncios_agencias WHERE agencia_id = 1", Integer.class);
        jdbc.update("INSERT INTO anuncios_agencias (agencia_id, anuncio_id, numero_anuncio, fecha_asignacion)"
                + " VALUES (1, ?, ?, CURRENT_DATE)", id, next);
    }

    @Test
    void theBonusIsTheSurchargePerLoadOverTheBaseLoadOfTheAgency() {
        // RF-08: SobrePr Carga Esp 80.000; Base Carga de Buesaco 1 = 125 -> Bonificacion 640 por kilo.
        publish("80000");

        AnnouncementResponse announcement = announcementService.findLatest(1L, fundRp(), SPECIAL);

        assertThat(announcement.bonus()).isEqualByComparingTo("640");
        assertThat(announcement.defectiveUnitPrice()).isEqualByComparingTo("6000");
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
