package com.cafeoccidente.backend.common.config;

import org.flywaydb.core.Flyway;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;

/**
 * Spring Boot 4.1.1 no trae autoconfiguracion de Flyway (no hay modulo
 * spring-boot-flyway en el BOM), por lo que las migraciones se disparan
 * manualmente aqui, antes de que se cree el datasource/JPA.
 *
 * Registrado via META-INF/spring.factories (no con @Component) para que
 * corra en CUALQUIER arranque de SpringApplication -- main(), @SpringBootTest,
 * etc. -- ya que ApplicationEnvironmentPreparedEvent se dispara antes de que
 * exista el ApplicationContext, cuando todavia no hay beans que escanear.
 */
public class FlywayMigrationListener implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        Environment env = event.getEnvironment();
        if (env.getProperty("spring.flyway.enabled", Boolean.class, true)) {
            Flyway.configure()
                    .dataSource(
                            env.getProperty("spring.datasource.url"),
                            env.getProperty("spring.datasource.username"),
                            env.getProperty("spring.datasource.password"))
                    .load()
                    .migrate();
        }
    }
}
