package com.chatapp.domain.message.dto;

import lombok.*;

/** Inbound STOMP payload for {@code /app/chat.typing} — indicates a user started or stopped typing. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TypingEvent {
    private Long roomId;
    private boolean typing;
}
