package com.cafeoccidente.backend.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

/**
 * El perfil de produccion arranca con las mismas variables que dev (base, JWT y admin), confia en los encabezados
 * X-Forwarded-* de Caddy y nunca manda la traza ni el mensaje interno de un error al cliente.
 */
@SpringBootTest
@ActiveProfiles("prod")
class ProdProfileIntegrationTest {

    @Autowired
    private Environment environment;

    @Test
    void prodProfileStartsBehindTheProxyWithoutLeakingErrors() {
        assertThat(environment.getProperty("server.forward-headers-strategy")).isEqualTo("framework");
        assertThat(environment.getProperty("server.error.include-stacktrace")).isEqualTo("never");
        assertThat(environment.getProperty("server.error.include-message")).isEqualTo("never");
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(environment.getProperty("jwt.secret")).isNotBlank();
    }
}
