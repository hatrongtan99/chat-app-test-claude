package com.chatapp.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * Stateless JWT operations: token generation, claim extraction, and validation.
 *
 * <p>Access tokens carry the username as subject and a {@code type=ACCESS} claim.
 * Refresh tokens carry the userId as subject, a {@code type=REFRESH} claim, and a
 * random {@code tokenId} UUID used as the Redis key discriminator for refresh-token rotation.
 *
 * <p>All parsing uses jjwt 0.13.x API ({@code Jwts.parser().verifyWith(...).build()}).
 * Signature verification and expiry checks are performed atomically during parsing.
 */
@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtProperties jwtProperties;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generates a short-lived access token for the given user.
     * Subject is the username; TTL is {@code jwt.access-expiration} ms.
     */
    public String generateAccessToken(UserDetails userDetails) {
        return Jwts.builder()
                .subject(userDetails.getUsername())
                .claim("type", "ACCESS")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtProperties.getAccessExpiration()))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Generates a long-lived refresh token for the given user.
     *
     * <p>Subject is the userId (string). The {@code tokenId} claim is a random UUID stored
     * in Redis as part of the key {@code refresh_token:{userId}:{tokenId}} to support
     * per-session revocation and reuse detection.
     */
    public String generateRefreshToken(Long userId) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("type", "REFRESH")
                .claim("tokenId", UUID.randomUUID().toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtProperties.getRefreshExpiration()))
                .signWith(getSigningKey())
                .compact();
    }

    /** Parses and signature-verifies the token, returning all claims. Throws on invalid/expired input. */
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** Extracts the username (subject) from an access token. */
    public String extractUsername(String token) {
        return extractAllClaims(token).getSubject();
    }

    /** Extracts the userId (subject) from a refresh token. */
    public Long extractUserId(String token) {
        return Long.valueOf(extractAllClaims(token).getSubject());
    }

    /**
     * Extracts the {@code tokenId} UUID from a refresh token.
     * Used to construct and look up the Redis key {@code refresh_token:{userId}:{tokenId}}.
     */
    public String extractTokenId(String token) {
        return extractAllClaims(token).get("tokenId", String.class);
    }

    public boolean isTokenExpired(String token) {
        return extractAllClaims(token).getExpiration().before(new Date());
    }

    /**
     * Returns {@code true} if the token signature is valid, the subject matches the given user,
     * and the token has not expired. Returns {@code false} for any parse or validation failure
     * rather than propagating exceptions — safe to use in filter chains.
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            String username = extractUsername(token);
            return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }
}
