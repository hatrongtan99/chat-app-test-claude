package com.chatapp.infrastructure.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * TTL-based online presence tracker backed by Redis.
 *
 * <p>A user is considered online while the key {@code user:online:{userId}} exists in Redis.
 * The key is set on STOMP connect and deleted on disconnect. Its TTL ({@value TTL_SECONDS}s)
 * is refreshed on every inbound STOMP frame via {@link #refresh} (called from
 * {@link com.chatapp.config.WebSocketConfig}), acting as a heartbeat to handle
 * ungraceful disconnects where no disconnect event fires.
 */
@Service
@RequiredArgsConstructor
public class PresenceService {

    private final StringRedisTemplate stringRedisTemplate;
    /** Presence key TTL. Must exceed the STOMP heartbeat interval to avoid false-offline readings. */
    private static final long TTL_SECONDS = 60L;

    /** Marks the user online by setting a Redis key with a sliding TTL. */
    public void setOnline(Long userId) {
        stringRedisTemplate.opsForValue().set(key(userId), "1", Duration.ofSeconds(TTL_SECONDS));
    }

    /** Marks the user offline by deleting the Redis presence key. */
    public void setOffline(Long userId) {
        stringRedisTemplate.delete(key(userId));
    }

    /** Resets the TTL on the presence key without changing its value — used as a heartbeat. */
    public void refresh(Long userId) {
        stringRedisTemplate.expire(key(userId), Duration.ofSeconds(TTL_SECONDS));
    }

    /** Returns {@code true} if the user's presence key exists in Redis. */
    public boolean isOnline(Long userId) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(key(userId)));
    }

    private String key(Long userId) {
        return "user:online:" + userId;
    }
}
