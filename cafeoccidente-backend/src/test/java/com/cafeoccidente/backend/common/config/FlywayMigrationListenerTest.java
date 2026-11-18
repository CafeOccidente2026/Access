package com.cafeoccidente.backend.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;
import org.springframework.boot.bootstrap.DefaultBootstrapContext;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.mock.env.MockEnvironment;

/** S2: Flyway usa su propio usuario si esta; si no, el de la app, como antes. */
class FlywayMigrationListenerTest {

    @Test
    void withFlywayDisabledItDoesNotTouchTheDatabase() {
        // Sin URL de base: si intentara migrar, fallaria.
        MockEnvironment env = new MockEnvironment().withProperty("spring.flyway.enabled", "false");
        ApplicationEnvironmentPreparedEvent event =
                new ApplicationEnvironmentPreparedEvent(new DefaultBootstrapContext(), new SpringApplication(), new String[0], env);

        assertThatCode(() -> new FlywayMigrationListener().onApplicationEvent(event)).doesNotThrowAnyException();
    }

    @Test
    void anEmptyFlywayUserFallsBackToTheAppUser() {
        assertThat(FlywayMigrationListener.blankToDefault(null, "app")).isEqualTo("app");
        assertThat(FlywayMigrationListener.blankToDefault("  ", "app")).isEqualTo("app");
        assertThat(FlywayMigrationListener.blankToDefault("migrador", "app")).isEqualTo("migrador");
    }
}
