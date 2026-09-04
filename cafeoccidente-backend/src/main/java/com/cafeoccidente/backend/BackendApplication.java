package com.cafeoccidente.backend;

import org.flywaydb.core.Flyway;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;

@SpringBootApplication
public class BackendApplication {

    // Spring Boot 4.1.1 ya no trae autoconfiguracion de Flyway (no hay modulo
    // spring-boot-flyway en el BOM), por lo que las migraciones se disparan
    // manualmente aqui, antes de que se cree el datasource/JPA.
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(BackendApplication.class);
        app.addListeners((ApplicationListener<ApplicationEnvironmentPreparedEvent>) event -> {
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
        });
        app.run(args);
    }
}
