package com.localrag.auth.security;

import com.localrag.auth.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;

@Slf4j
@Component
public class JwtUtils {

    private final SecretKey key;
    private final int expirationDays;

    public JwtUtils(JwtProperties properties) {
        String raw = properties.getJwtSecretKey();
        if (raw == null || raw.isBlank()) {
            byte[] randomBytes = new byte[32];
            new SecureRandom().nextBytes(randomBytes);
            raw = Base64.getEncoder().encodeToString(randomBytes);
            log.warn("jwt-secret-key not configured, using random key (all tokens invalid on restart)");
        }
        this.key = new SecretKeySpec(Base64.getDecoder().decode(raw), "HmacSHA256");
        this.expirationDays = properties.getJwtExpirationDays();
    }

    public String generate(Long userId, String username, String role) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationDays * 86400000L))
                .signWith(key)
                .compact();
    }

    public JwtPayload validate(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return new JwtPayload(
                Long.parseLong(claims.getSubject()),
                claims.get("username", String.class),
                claims.get("role", String.class));
    }

    public boolean isValid(String token) {
        try {
            validate(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public record JwtPayload(Long userId, String username, String role) {}
}
