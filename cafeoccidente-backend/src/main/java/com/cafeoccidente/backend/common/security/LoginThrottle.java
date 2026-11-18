package com.cafeoccidente.backend.common.security;

import com.cafeoccidente.backend.common.exception.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * S4 (seguridad, 13-nov): 5 intentos fallidos del usuario en 15 min lo dejan esperando 15 min. La IP espera con
 * app.login.max-failures-per-ip (30): en una agencia todos los equipos salen por la misma IP, y un solo usuario no
 * debe dejarla por fuera. Un acierto reinicia el contador del usuario; el Administrador desbloquea antes desde Usuarios
 * (el usuario y las IP desde donde fallo).
 *
 * ponytail: en memoria y con un solo candado; sirve para un backend. Con varios, pasarlo a la base o a Redis.
 */
@Component
public class LoginThrottle {

    static final int MAX_FAILURES = 5;
    static final Duration WINDOW = Duration.ofMinutes(15);
    static final String LOCKED_CODE = "LOGIN_LOCKED";
    /** Tope de entradas antes de limpiar las vencidas (nombres de usuario inventados no llenan la memoria). */
    private static final int PURGE_ABOVE = 10_000;

    private final Clock clock;
    private final int maxFailuresPerIp;
    private final Map<String, Attempts> byUser = new HashMap<>();
    private final Map<String, Attempts> byIp = new HashMap<>();

    public LoginThrottle(Clock clock, @Value("${app.login.max-failures-per-ip:30}") int maxFailuresPerIp) {
        this.clock = clock;
        this.maxFailuresPerIp = maxFailuresPerIp;
    }

    /** Antes de revisar la contrasena: si el usuario o la IP estan esperando, 429 con cuanto falta. */
    public synchronized void check(String username, String ip) {
        Instant now = clock.instant();
        Instant until = later(lockedUntil(byUser.get(key(username)), now), lockedUntil(byIp.get(ip), now));
        if (until != null) {
            long minutes = Math.max(1, (Duration.between(now, until).getSeconds() + 59) / 60);
            throw new ApiException("Demasiados intentos fallidos. Intente de nuevo en " + minutes
                    + (minutes == 1 ? " minuto." : " minutos."), HttpStatus.TOO_MANY_REQUESTS, LOCKED_CODE);
        }
    }

    public synchronized void failed(String username, String ip) {
        Instant now = clock.instant();
        if (byUser.size() + byIp.size() > PURGE_ABOVE) {
            byUser.values().removeIf(a -> a.expired(now));
            byIp.values().removeIf(a -> a.expired(now));
        }
        String user = key(username);
        byUser.computeIfAbsent(user, k -> new Attempts()).fail(now, MAX_FAILURES);
        Attempts fromIp = byIp.computeIfAbsent(ip, k -> new Attempts());
        fromIp.fail(now, maxFailuresPerIp);
        fromIp.users.add(user);
    }

    public synchronized void succeeded(String username) {
        byUser.remove(key(username));
    }

    /** El Administrador desbloquea al usuario y a las IP desde donde se fallo con ese usuario. */
    public synchronized void unlock(String username) {
        String user = key(username);
        byUser.remove(user);
        byIp.values().removeIf(a -> a.users.contains(user));
    }

    private static String key(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    private static Instant lockedUntil(Attempts attempts, Instant now) {
        return attempts != null && attempts.lockedUntil != null && now.isBefore(attempts.lockedUntil)
                ? attempts.lockedUntil : null;
    }

    private static Instant later(Instant a, Instant b) {
        return a == null || (b != null && b.isAfter(a)) ? b : a;
    }

    /** Fallos dentro de la ventana que empezo con el primero; al llegar al tope queda esperando WINDOW. */
    private static final class Attempts {
        private Instant windowStart;
        private int count;
        private Instant lockedUntil;
        private final Set<String> users = new HashSet<>();

        void fail(Instant now, int max) {
            if (windowStart == null || expired(now)) {
                windowStart = now;
                count = 0;
                lockedUntil = null;
                users.clear();
            }
            if (++count >= max) {
                lockedUntil = now.plus(WINDOW);
            }
        }

        boolean expired(Instant now) {
            Instant end = lockedUntil != null ? lockedUntil : windowStart.plus(WINDOW);
            return !now.isBefore(end);
        }
    }
}
