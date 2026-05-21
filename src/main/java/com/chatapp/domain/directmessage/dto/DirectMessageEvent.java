package com.chatapp.domain.directmessage.dto;

import lombok.*;

import java.time.LocalDateTime;

/**
 * Envelope published to the Redis {@code chat:dm} channel and forwarded by
 * {@link com.chatapp.infrastructure.redis.RedisDmSubscriber} to each participant's
 * personal STOMP queue {@code /user/queue/direct-messages}.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DirectMessageEvent {
    private String eventType; // always "DM_MESSAGE"
    private DirectMessageResponse message;
    private String senderUsername;
    private String recipientUsername;
    private LocalDateTime timestamp;
}
