package com.cafeoccidente.backend.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cafeoccidente.backend.common.exception.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** S4: 5 fallos en 15 min, por usuario y por IP, dejan esperando 15 min (respuesta del 13-nov). */
class LoginThrottleTest {

    /** Reloj que el test adelanta a mano. */
    private static final class TestClock extends Clock {
        private Instant now = Instant.parse("2026-11-13T12:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    private final TestClock clock = new TestClock();
    private final LoginThrottle throttle = new LoginThrottle(clock);

    private void fail(String user, String ip, int times) {
        for (int i = 0; i < times; i++) {
            throttle.failed(user, ip);
        }
    }

    private void locked(String user, String ip, String minutes) {
        assertThatThrownBy(() -> throttle.check(user, ip)).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            assertThat(e.getCode()).isEqualTo(LoginThrottle.LOCKED_CODE);
            assertThat(e.getMessage()).isEqualTo("Demasiados intentos fallidos. Intente de nuevo en " + minutes + ".");
        });
    }

    private void open(String user, String ip) {
        assertThatCode(() -> throttle.check(user, ip)).doesNotThrowAnyException();
    }

    @Test
    void fourFailuresStillLetTry() {
        fail("ana", "10.0.0.1", 4);
        open("ana", "10.0.0.1");
    }

    @Test
    void theFifthFailureLocksTheUserFifteenMinutes() {
        fail("ana", "10.0.0.1", 5);
        locked("ana", "10.0.0.2", "15 minutos");
        locked("ANA ", "10.0.0.3", "15 minutos");
        open("beto", "10.0.0.4");
        clock.advance(Duration.ofSeconds(14 * 60 + 1));
        locked("ana", "10.0.0.2", "1 minuto");
        clock.advance(Duration.ofSeconds(59));
        open("ana", "10.0.0.2");
    }

    @Test
    void fiveFailuresFromOneIpWithDifferentUsersLockTheIp() {
        for (String user : new String[] {"a", "b", "c", "d", "e"}) {
            throttle.failed(user, "10.0.0.9");
        }
        locked("f", "10.0.0.9", "15 minutos");
        open("f", "10.0.0.8");
    }

    @Test
    void failuresOlderThanTheWindowDoNotAddUp() {
        fail("ana", "10.0.0.1", 4);
        clock.advance(Duration.ofMinutes(15));
        fail("ana", "10.0.0.1", 4);
        open("ana", "10.0.0.1");
    }

    @Test
    void aSuccessResetsTheUser() {
        fail("ana", "10.0.0.1", 4);
        throttle.succeeded("ana");
        fail("ana", "10.0.0.2", 4);
        open("ana", "10.0.0.2");
    }

    @Test
    void theAdministratorUnlocksTheUserAndItsIps() {
        fail("ana", "10.0.0.1", 5);
        fail("beto", "10.0.0.7", 5);
        throttle.unlock("Ana");
        open("ana", "10.0.0.1");
        locked("beto", "10.0.0.7", "15 minutos");
    }

    @Test
    void manyInventedUsersAreCleanedOnceTheyExpire() {
        for (int i = 0; i <= 10_000; i++) {
            throttle.failed("u" + i, "10.1.0.1");
        }
        clock.advance(Duration.ofMinutes(16));
        throttle.failed("otro", "10.2.0.1");
        open("u1", "10.1.0.1");
        open(null, "10.1.0.1");
    }
}
