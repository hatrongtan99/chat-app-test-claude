package com.chatapp.domain.directmessage.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** STOMP payload for sending a direct message via WebSocket {@code /app/dm.send}. */
@Getter
@Setter
public class DirectMessageSendRequest {

    @NotNull
    private Long recipientId;

    @NotBlank
    private String content;
}
