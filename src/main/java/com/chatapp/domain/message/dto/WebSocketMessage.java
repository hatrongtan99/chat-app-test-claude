package com.chatapp.domain.message.dto;

import lombok.*;

import java.time.LocalDateTime;

/**
 * Envelope for all real-time events published to the Redis {@code chat:messages} channel
 * and forwarded to STOMP topic {@code /topic/rooms/{roomId}} or {@code /topic/presence}.
 *
 * <p>Known {@code eventType} values: {@code CHAT_MESSAGE}, {@code SYSTEM_MESSAGE},
 * {@code TYPING}, {@code PRESENCE}. The {@code extra} field carries event-specific payloads
 * (e.g., a {@code Boolean} for typing state or online status).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebSocketMessage {
    private String eventType;
    private Long roomId;
    private MessageResponse message;
    private Long userId;
    private String username;
    private LocalDateTime timestamp;
    private Object extra;
}
