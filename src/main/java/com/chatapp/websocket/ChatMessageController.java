package com.chatapp.websocket;

import com.chatapp.domain.message.dto.SendMessageRequest;
import com.chatapp.domain.message.dto.TypingEvent;
import com.chatapp.domain.message.dto.WebSocketMessage;
import com.chatapp.domain.message.service.MessageService;
import com.chatapp.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.LocalDateTime;

/**
 * STOMP {@code @MessageMapping} controller handling inbound chat frames.
 *
 * <p>The {@code Principal} is set during STOMP {@code CONNECT} by the
 * {@link com.chatapp.config.WebSocketConfig} channel interceptor. A {@code null} principal
 * means the client connected without a valid JWT and the frame is silently dropped.
 *
 * <p>Typing events bypass {@link MessageService} and are published directly to Redis
 * so they are fanned out to subscribers without being persisted to MySQL.
 */
@Controller
@RequiredArgsConstructor
public class ChatMessageController {

    private final MessageService messageService;

    @Qualifier("pubSubRedisTemplate")
    private final RedisTemplate<String, Object> pubSubRedisTemplate;

    private static final String CHAT_CHANNEL = "chat:messages";

    /**
     * Handles {@code /app/chat.send}: persists the message and triggers Redis pub/sub delivery.
     * Delegates to {@link MessageService#sendMessage} which saves to MySQL then publishes to Redis.
     */
    @MessageMapping("/chat.send")
    public void handleChatMessage(@Payload SendMessageRequest request, Principal principal) {
        if (principal == null) return;
        User user = (User) ((org.springframework.security.authentication.UsernamePasswordAuthenticationToken) principal).getPrincipal();
        messageService.sendMessage(request.getRoomId(), user.getId(), request);
    }

    /**
     * Handles {@code /app/chat.typing}: broadcasts a transient typing indicator via Redis.
     * Not persisted — subscribers receive it in real-time only.
     */
    @MessageMapping("/chat.typing")
    public void handleTyping(@Payload TypingEvent event, Principal principal) {
        if (principal == null) return;
        User user = (User) ((org.springframework.security.authentication.UsernamePasswordAuthenticationToken) principal).getPrincipal();
        WebSocketMessage wsMsg = WebSocketMessage.builder()
                .eventType("TYPING")
                .roomId(event.getRoomId())
                .userId(user.getId())
                .username(user.getUsername())
                .timestamp(LocalDateTime.now())
                .extra(event.isTyping())
                .build();
        pubSubRedisTemplate.convertAndSend(CHAT_CHANNEL, wsMsg);
    }
}
