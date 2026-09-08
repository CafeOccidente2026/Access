package com.cafeoccidente.backend.common.security;

import com.cafeoccidente.backend.users.entity.Role;
import com.cafeoccidente.backend.users.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Genera y valida tokens JWT. */
@Service
public class JwtService {

    private static final long ACCESS_TOKEN_EXPIRATION_MS = 15 * 60 * 1000L;
    private static final long REFRESH_TOKEN_EXPIRATION_MS = 7 * 24 * 60 * 60 * 1000L;
    private static final String ROLE_CLAIM = "role";
    private static final String TYPE_CLAIM = "type";
    private static final String ACCESS_TYPE = "access";
    private static final String REFRESH_TYPE = "refresh";

    private final SecretKey key;

    public JwtService(@Value("${security.jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(User user) {
        return buildToken(user, ACCESS_TOKEN_EXPIRATION_MS, ACCESS_TYPE);
    }

    public String generateRefreshToken(User user) {
        return buildToken(user, REFRESH_TOKEN_EXPIRATION_MS, REFRESH_TYPE);
    }

    public boolean isAccessTokenValid(String token) {
        return isTokenValid(token, ACCESS_TYPE);
    }

    public boolean isRefreshTokenValid(String token) {
        return isTokenValid(token, REFRESH_TYPE);
    }

    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    public Role extractRole(String token) {
        return Role.valueOf(parseClaims(token).get(ROLE_CLAIM, String.class));
    }

    private String buildToken(User user, long expirationMs, String type) {
        Date now = new Date();
        return Jwts.builder()
                .subject(user.getUsername())
                .claim(ROLE_CLAIM, user.getRole().name())
                .claim(TYPE_CLAIM, type)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(key)
                .compact();
    }

    private boolean isTokenValid(String token, String expectedType) {
        try {
            return expectedType.equals(parseClaims(token).get(TYPE_CLAIM, String.class));
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
