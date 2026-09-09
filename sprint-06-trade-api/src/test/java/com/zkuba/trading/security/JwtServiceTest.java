package com.zkuba.trading.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private JwtService jwtService;
    private final String secret = "super-secret-key-that-is-at-least-256-bits-long-for-hmac-sha-256";
    private final long expirationMs = 3600000L;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(secret, expirationMs);
    }

    @Test
    @DisplayName("generate and parse should correctly embed and extract claims")
    void generateAndParse_Success() {
        Long userId = 42L;
        String username = "trader_joe";
        String role = "ROLE_USER";

        String token = jwtService.generate(userId, username, role);

        assertThat(token).isNotBlank();

        Claims claims = jwtService.parse(token);
        assertThat(claims.getSubject()).isEqualTo(username);
        assertThat(claims.get("userId", Long.class)).isEqualTo(userId);
        assertThat(claims.get("role", String.class)).isEqualTo(role);
        assertThat(claims.getExpiration()).isNotNull();
    }

    @Test
    @DisplayName("expirationMs should return the configured expiration time in milliseconds")
    void expirationMs_ReturnsConfiguredValue() {
        assertThat(jwtService.expirationMs()).isEqualTo(expirationMs);
    }
}
