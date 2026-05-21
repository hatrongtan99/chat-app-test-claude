package com.chatapp.domain.directmessage.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Serialisable DTO returned by REST endpoints and embedded in {@link DirectMessageEvent}. */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DirectMessageResponse {
    private Long id;
    private Long senderId;
    private String senderUsername;
    private String senderDisplayName;
    private Long recipientId;
    private String recipientUsername;
    private String content;
    private LocalDateTime createdAt;
}
