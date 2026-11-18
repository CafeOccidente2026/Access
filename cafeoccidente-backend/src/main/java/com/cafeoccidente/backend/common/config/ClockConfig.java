package com.cafeoccidente.backend.common.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Reloj del negocio en hora de Colombia. El servidor y la base corren en UTC: con LocalDate.now() sin zona, una compra
 * despues de las 7 p. m. quedaria con la fecha del dia siguiente. Toda fecha del negocio sale de LocalDate.now(clock).
 */
@Configuration
public class ClockConfig {

    public static final ZoneId COLOMBIA = ZoneId.of("America/Bogota");

    @Bean
    Clock clock() {
        return Clock.system(COLOMBIA);
    }
}
