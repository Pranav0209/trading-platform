package com.zkuba.trading.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final SecretKey key;
    private final long expirationMs;

    public JwtService(
            @Value("${jwt.secret}") String secret, @Value("${jwt.expiration-ms}") long exp) {
        key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        expirationMs = exp;
    }

    public String generate(Long id, String username, String role) {
        Date n = new Date();
        return Jwts.builder()
                .subject(username)
                .claim("userId", id)
                .claim("role", role)
                .issuedAt(n)
                .expiration(new Date(n.getTime() + expirationMs))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    public long expirationMs() {
        return expirationMs;
    }
}
