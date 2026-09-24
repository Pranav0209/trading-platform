package com.zkuba.trading.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private JwtService jwtService;
    private final String secret = "super-secret-key-that-is-at-least-256-bits-long-for-hmac-sha-256";
    private final long expirationMs = 3600000L;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        jwtService.init(secret);
        ReflectionTestUtils.setField(jwtService, "expirationMs", expirationMs);
    }

    @Test
    @DisplayName("generate and parse should correctly embed and extract claims")
    void generateAndParse_Success() {
        Long userId = 42L;
        String username = "trader_joe";
        String role = "ROLE_USER";

        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        String token = Jwts.builder()
                .subject(username)
                .claim("userId", userId)
                .claim("role", role)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(key)
                .compact();

        assertThat(token).isNotBlank();

        Claims claims = jwtService.parse(token);
        assertThat(claims.getSubject()).isEqualTo(username);
        assertThat(claims.get("userId", Long.class)).isEqualTo(userId);
        assertThat(claims.get("role", String.class)).isEqualTo(role);
        assertThat(claims.getExpiration()).isNotNull();
    }
}
