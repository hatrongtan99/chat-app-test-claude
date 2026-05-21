package com.chatapp.infrastructure.redis;

import com.chatapp.domain.message.dto.WebSocketMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis pub/sub subscriber that bridges the {@code chat:messages} channel to STOMP topics.
 *
 * <p>Receives serialised {@link WebSocketMessage} JSON from Redis (published by
 * {@link com.chatapp.domain.message.service.MessageService}) and forwards each message
 * to the STOMP topic {@code /topic/rooms/{roomId}}. This enables real-time delivery
 * across multiple application instances sharing the same Redis broker.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisMessageSubscriber implements MessageListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Deserializes the Redis message payload and broadcasts it to the appropriate STOMP topic.
     * Errors are logged and swallowed to keep the listener alive for subsequent messages.
     */
    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            WebSocketMessage wsMsg = objectMapper.readValue(message.getBody(), WebSocketMessage.class);
            messagingTemplate.convertAndSend("/topic/rooms/" + wsMsg.getRoomId(), wsMsg);
        } catch (Exception e) {
            log.error("Failed to process Redis message", e);
        }
    }
}
