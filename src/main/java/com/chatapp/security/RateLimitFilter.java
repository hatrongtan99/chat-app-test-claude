package com.chatapp.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * Per-IP sliding-window rate limiter for authentication endpoints, backed by Redis.
 *
 * <p>Applies only to {@code POST /api/auth/login} and {@code POST /api/auth/register}.
 * Uses a Redis counter keyed by {@code rate_limit:{ip}:auth:{minuteBucket}} where
 * {@code minuteBucket} is the current epoch-minute, giving a tumbling-window approximation.
 * The key TTL is set to {@code app.rate-limit.auth-window-seconds} on the first increment,
 * ensuring automatic cleanup without a scheduled job.
 *
 * <p>Runs before {@link JwtAuthFilter} so unauthenticated brute-force attempts are
 * rejected before any DB or JWT work is performed. Limits are shared across all
 * application instances because the counter lives in Redis.
 *
 * <p>Defaults: 5 requests per 60-second window. Override via
 * {@code app.rate-limit.auth-max-requests} and {@code app.rate-limit.auth-window-seconds}.
 */
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final StringRedisTemplate stringRedisTemplate;

    @Value("${app.rate-limit.auth-max-requests:5}")
    private int maxRequests;

    @Value("${app.rate-limit.auth-window-seconds:60}")
    private long windowSeconds;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        String method = request.getMethod();

        boolean isRateLimited = "POST".equals(method) &&
                (path.equals("/api/auth/login") || path.equals("/api/auth/register"));

        if (!isRateLimited) {
            filterChain.doFilter(request, response);
            return;
        }

        String ip = request.getRemoteAddr();
        // Tumbling window: bucket changes each minute, giving at most 2× the limit in a sliding minute.
        String minute = String.valueOf(System.currentTimeMillis() / 60_000);
        String key = "rate_limit:" + ip + ":auth:" + minute;

        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            // Set TTL only on first increment to avoid resetting the window on each request.
            stringRedisTemplate.expire(key, Duration.ofSeconds(windowSeconds));
        }

        if (count != null && count > maxRequests) {
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"message\":\"Too many requests, please try again later\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
