package com.chatapp.domain.message.dto;

import com.chatapp.domain.message.entity.MessageType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** REST and STOMP payload for sending a message. {@code type} defaults to {@code TEXT} if omitted. */
@Getter
@Setter
public class SendMessageRequest {

    @NotNull
    private Long roomId;

    @NotBlank
    private String content;

    private MessageType type = MessageType.TEXT;
}
