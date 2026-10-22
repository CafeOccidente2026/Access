package com.cafeoccidente.backend.controlrecord;

import static org.assertj.core.api.Assertions.assertThat;

import com.cafeoccidente.backend.controlrecord.entity.ControlRecord;
import com.cafeoccidente.backend.controlrecord.service.ControlRecordService;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * V6 (HU-07, D1, D13) sobre la semilla: cada parametro en su columna, el umbral de Access y los fondos
 * con su nombre del RF. Solo usa la semilla de V2, asi que corre tambien en CI.
 */
@SpringBootTest
class ControlRecordSeedTest {

    @Autowired
    private ControlRecordService service;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void buesacoAndElTamboKeepKgExcelso70FactorBase94AndThreshold9333() {
        for (long agencyId : new long[] {1, 4}) {
            ControlRecord record = service.getActive(agencyId);
            assertThat(record.getExcelsoKg()).as("Kg de Excelso").isEqualTo(70);
            assertThat(record.getBaseFactor()).as("Factor Base").isEqualByComparingTo("94");
            assertThat(record.getSpecialtyThreshold()).as("Umbral").isEqualByComparingTo("93.33");
        }
    }

    @Test
    void greenCoffeeValuesStartAtZeroUntilTheAdministratorTypesThem() {
        Map<String, Object> green = jdbc.queryForMap("SELECT peso_tot_alm_cv, peso_tot_pasilla_cv, peso_alm_sana_cv,"
                + " pr_alm_sana_cv, pr_alm_defec_cv FROM registros_control WHERE agencia_id = 1");
        green.forEach((column, value) -> assertThat((BigDecimal) value).as(column).isZero());
    }

    @Test
    void fundsHaveTheirRfNames() {
        assertThat(jdbc.queryForObject("SELECT nombre FROM fondos WHERE codigo = 'RP'", String.class))
                .isEqualTo("Recursos Propios");
        assertThat(jdbc.queryForObject("SELECT nombre FROM fondos WHERE codigo = 'LF'", String.class))
                .isEqualTo("Línea de Financiamiento");
    }
}
