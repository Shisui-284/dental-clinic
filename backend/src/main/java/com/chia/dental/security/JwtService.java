package com.chia.dental.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

@Service
public class JwtService {

    private final SecretKey secretKey;
    private final long expirationMs;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms}") long expirationMs) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generateStaffToken(Long staffId, String username, String roleName) {
        Date now = new Date();
        return Jwts.builder()
                .subject(username)
                .claims(Map.of(
                        "id", staffId,
                        "role", roleName,
                        "type", "STAFF"
                ))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(secretKey)
                .compact();
    }

    public String generateCustomerToken(Long customerId, String phone) {
        Date now = new Date();
        return Jwts.builder()
                .subject(phone)
                .claims(Map.of(
                        "id", customerId,
                        "role", "CUSTOMER",
                        "type", "CUSTOMER"
                ))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(secretKey)
                .compact();
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public UserPrincipal toPrincipal(Claims claims) {
        return UserPrincipal.builder()
                .id(((Number) claims.get("id")).longValue())
                .username(claims.getSubject())
                .role((String) claims.get("role"))
                .type((String) claims.get("type"))
                .build();
    }
}

