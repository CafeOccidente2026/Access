package com.cafeoccidente.backend.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Genera y valida tokens JWT. */
@Component
public class JwtService {

    static final String SHORT_SECRET = "JWT_SECRET debe tener al menos 32 caracteres (por ejemplo: openssl rand -base64 48)";
    private static final int MIN_SECRET_LENGTH = 32;
    private static final String TOKEN_VERSION = "tv";

    private final SecretKey key;
    private final long expirationMs;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms:28800000}") long expirationMs) {
        // S6: con un secreto corto la firma se puede adivinar; el backend no arranca.
        if (secret == null || secret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(SHORT_SECRET);
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generateToken(SecurityUser user) {
        Date now = new Date();
        return Jwts.builder()
                .subject(user.getUsername())
                .claim("userId", user.getUserId())
                .claim(TOKEN_VERSION, user.getUser().getTokenVersion())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(key)
                .compact();
    }

    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    /** S6: la version con que se emitio el token; null en los emitidos antes de S6. */
    public Integer extractTokenVersion(String token) {
        return parseClaims(token).get(TOKEN_VERSION, Integer.class);
    }

    public boolean isValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
