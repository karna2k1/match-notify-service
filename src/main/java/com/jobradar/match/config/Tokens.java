package com.jobradar.match.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Parses user JWTs and mints service tokens + email feedback tokens, all with the shared secret. */
@Component
public class Tokens {
    private final SecretKey key;

    public Tokens(@Value("${app.jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    /** Short-lived token for calling core-api /internal/**. */
    public String serviceToken() {
        Instant now = Instant.now();
        return Jwts.builder().subject("match-notify-service").claim("svc", true)
                .issuedAt(Date.from(now)).expiration(Date.from(now.plus(Duration.ofMinutes(5))))
                .signWith(key).compact();
    }

    /** Signed Save/Ignore link token for emails (no login needed, valid 14 days). */
    public String feedbackToken(long matchId, String action) {
        Instant now = Instant.now();
        return Jwts.builder().subject("feedback").claim("mid", matchId).claim("act", action)
                .issuedAt(Date.from(now)).expiration(Date.from(now.plus(Duration.ofDays(14))))
                .signWith(key).compact();
    }
}
