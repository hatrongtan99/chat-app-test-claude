package com.chatapp.websocket;

import com.chatapp.domain.message.dto.WebSocketMessage;
import com.chatapp.domain.user.entity.User;
import com.chatapp.infrastructure.redis.PresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.time.LocalDateTime;

/**
 * Listens for STOMP session lifecycle events to maintain presence state and broadcast
 * online/offline notifications to {@code /topic/presence}.
 *
 * <p>On connect, the user is marked online in Redis and a {@code PRESENCE} event is broadcast.
 * On disconnect (graceful or server-initiated), the user is marked offline.
 * Presence TTL is separately refreshed on inbound frames via
 * {@link com.chatapp.config.WebSocketConfig} to handle ungraceful disconnects.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final PresenceService presenceService;
    private final SimpMessagingTemplate messagingTemplate;

    @EventListener
    public void handleConnect(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        User user = extractUser(accessor);
        if (user != null) {
            presenceService.setOnline(user.getId());
            broadcastPresence(user.getId(), user.getUsername(), true);
            log.debug("User connected: {}", user.getUsername());
        }
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        User user = extractUser(accessor);
        if (user != null) {
            presenceService.setOffline(user.getId());
            broadcastPresence(user.getId(), user.getUsername(), false);
            log.debug("User disconnected: {}", user.getUsername());
        }
    }

    private User extractUser(StompHeaderAccessor accessor) {
        if (accessor.getUser() instanceof UsernamePasswordAuthenticationToken auth
                && auth.getPrincipal() instanceof User user) {
            return user;
        }
        return null;
    }

    private void broadcastPresence(Long userId, String username, boolean online) {
        WebSocketMessage wsMsg = WebSocketMessage.builder()
                .eventType("PRESENCE")
                .userId(userId)
                .username(username)
                .timestamp(LocalDateTime.now())
                .extra(online)
                .build();
        messagingTemplate.convertAndSend("/topic/presence", wsMsg);
    }
}
