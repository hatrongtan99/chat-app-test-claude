package com.chatapp.domain.auth.service;

import com.chatapp.common.exception.AppException;
import com.chatapp.common.exception.ErrorCode;
import com.chatapp.domain.auth.dto.AuthResponse;
import com.chatapp.domain.auth.dto.LoginRequest;
import com.chatapp.domain.auth.dto.RefreshRequest;
import com.chatapp.domain.auth.dto.RegisterRequest;
import com.chatapp.domain.user.entity.User;
import com.chatapp.domain.user.repository.UserRepository;
import com.chatapp.security.JwtProperties;
import com.chatapp.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Set;

/**
 * Handles user registration, login, token refresh, and logout.
 *
 * <p>Implements refresh token rotation: each successful {@link #refresh} call invalidates
 * the consumed token and issues a new pair. If a refresh token is presented whose Redis key
 * no longer exists, a token-reuse attack is assumed and all sessions for that user are revoked.
 *
 * <p>Refresh tokens are stored in Redis under the key
 * {@code refresh_token:{userId}:{tokenId}} with a TTL equal to {@code jwt.refresh-expiration}.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final AuthenticationManager authenticationManager;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * Registers a new user and returns an initial token pair.
     *
     * @throws AppException {@code DUPLICATE_USERNAME} or {@code DUPLICATE_EMAIL} on conflict
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new AppException(ErrorCode.DUPLICATE_USERNAME);
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new AppException(ErrorCode.DUPLICATE_EMAIL);
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .displayName(request.getDisplayName() != null ? request.getDisplayName() : request.getUsername())
                .build();
        user = userRepository.save(user);
        return buildAndStoreTokens(user);
    }

    /**
     * Authenticates credentials via Spring's {@code AuthenticationManager} and issues a token pair.
     *
     * @throws AppException {@code INVALID_CREDENTIALS} on bad username or password
     */
    public AuthResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
        } catch (BadCredentialsException e) {
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        }

        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        return buildAndStoreTokens(user);
    }

    /**
     * Rotates a refresh token: deletes the consumed Redis key and issues a new token pair.
     *
     * <p>If the Redis key is absent (token already consumed or never stored), all refresh
     * tokens for the user are revoked to neutralise a potential token-reuse attack.
     *
     * @throws AppException {@code INVALID_TOKEN} on expiry, missing Redis key, or parse failure
     */
    public AuthResponse refresh(RefreshRequest request) {
        String token = request.getRefreshToken();
        try {
            if (jwtService.isTokenExpired(token)) {
                throw new AppException(ErrorCode.INVALID_TOKEN);
            }
            Long userId = jwtService.extractUserId(token);
            String tokenId = jwtService.extractTokenId(token);
            String key = refreshKey(userId, tokenId);

            Boolean exists = stringRedisTemplate.hasKey(key);
            if (Boolean.FALSE.equals(exists)) {
                // Token reuse detected: the key was already deleted by a prior refresh or logout.
                // Revoke all remaining sessions for this user as a security precaution.
                Set<String> keys = stringRedisTemplate.keys("refresh_token:" + userId + ":*");
                if (keys != null && !keys.isEmpty()) {
                    stringRedisTemplate.delete(keys);
                }
                throw new AppException(ErrorCode.INVALID_TOKEN);
            }

            stringRedisTemplate.delete(key);
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
            return buildAndStoreTokens(user);
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw new AppException(ErrorCode.INVALID_TOKEN);
        }
    }

    /**
     * Invalidates the given refresh token by deleting its Redis key.
     * Ignores errors silently — logout is always considered successful from the client's perspective.
     */
    public void logout(RefreshRequest request) {
        try {
            String token = request.getRefreshToken();
            Long userId = jwtService.extractUserId(token);
            String tokenId = jwtService.extractTokenId(token);
            stringRedisTemplate.delete(refreshKey(userId, tokenId));
        } catch (Exception ignored) {
        }
    }

    /**
     * Generates an access/refresh token pair and persists the refresh token's Redis key.
     * The key {@code refresh_token:{userId}:{tokenId}} expires at {@code jwt.refresh-expiration} ms.
     */
    private AuthResponse buildAndStoreTokens(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user.getId());
        String tokenId = jwtService.extractTokenId(refreshToken);
        long ttlMs = jwtProperties.getRefreshExpiration();

        stringRedisTemplate.opsForValue().set(
                refreshKey(user.getId(), tokenId),
                "1",
                Duration.ofMillis(ttlMs));

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .accessExpiresIn(jwtProperties.getAccessExpiration())
                .build();
    }

    private String refreshKey(Long userId, String tokenId) {
        return "refresh_token:" + userId + ":" + tokenId;
    }
}
