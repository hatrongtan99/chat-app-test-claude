package com.chatapp.domain.message.dto;

import com.chatapp.domain.message.entity.MessageType;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Serializable message DTO returned by REST endpoints and embedded in {@link WebSocketMessage}. */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageResponse {
  private Long id;
  private Long roomId;
  private Long senderId;
  private String senderUsername;
  private String senderDisplayName;
  private String content;
  private MessageType type;
  private LocalDateTime createdAt;
}
