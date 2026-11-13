package com.cafeoccidente.backend.common.config;

import java.util.Map;
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
 *
 * S2 (seguridad): Flyway puede usar su propio usuario (spring.flyway.user, el rol migrador, dueno del esquema) y la
 * app el suyo, sin permisos de esquema (deploy/roles-bd.sql). Sin spring.flyway.user se usa el de la app, como antes.
 */
public class FlywayMigrationListener implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        Environment env = event.getEnvironment();
        if (!env.getProperty("spring.flyway.enabled", Boolean.class, true)) {
            return;
        }
        String url = env.getProperty("spring.datasource.url");
        String appUser = env.getProperty("spring.datasource.username");
        String user = blankToDefault(env.getProperty("spring.flyway.user"), appUser);
        String password = blankToDefault(env.getProperty("spring.flyway.password"), env.getProperty("spring.datasource.password"));
        // appUser lo usa el callback afterMigrate.sql: la app no escribe el historial de Flyway.
        Flyway.configure().dataSource(url, user, password).placeholders(Map.of("appUser", appUser)).load().migrate();
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
