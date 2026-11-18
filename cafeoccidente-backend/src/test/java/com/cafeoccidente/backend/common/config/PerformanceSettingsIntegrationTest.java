package com.cafeoccidente.backend.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * R1 (rendimiento): con 50 usuarios el pool de 10 conexiones se agotaba y algunas peticiones esperaban 30 s y daban
 * 500 (medicion R0/R1). Pool de 20 (DB_POOL_MAX), espera maxima de 10 s, hilos virtuales y sin Open Session in View.
 * V28: el acumulado diario de la retencion (cada compra) usa un indice por cedula y fecha en vez de recorrer la tabla.
 */
@SpringBootTest
class PerformanceSettingsIntegrationTest {

    @Autowired
    private Environment environment;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void thePoolHasTwentyConnectionsAndWaitsAtMostTenSeconds() {
        HikariDataSource hikari = (HikariDataSource) dataSource;
        assertThat(hikari.getMaximumPoolSize()).isEqualTo(20);
        assertThat(hikari.getMinimumIdle()).isEqualTo(5);
        assertThat(hikari.getConnectionTimeout()).isEqualTo(10_000);
    }

    @Test
    void virtualThreadsOnAndOpenSessionInViewOff() {
        assertThat(environment.getProperty("spring.threads.virtual.enabled")).isEqualTo("true");
        assertThat(environment.getProperty("spring.jpa.open-in-view")).isEqualTo("false");
    }

    @Test
    void theDailyGrowerTotalsUseAnIndexByIdNumberAndDate() {
        List<String> indexes = jdbc.queryForList(
                "SELECT indexname FROM pg_indexes WHERE indexdef LIKE '%(cedula, fecha_compra)%'", String.class);
        assertThat(indexes).contains("ix_compras_cafe_seco_cedula_fecha_compra",
                "ix_compras_cafe_verde_cedula_fecha_compra", "ix_compras_pasilla_cedula_fecha_compra");
    }
}
