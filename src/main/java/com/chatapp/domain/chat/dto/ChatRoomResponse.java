package com.chatapp.domain.chat.dto;

import com.chatapp.domain.chat.entity.RoomType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/** API response for a chat room, including computed fields {@code memberCount} and {@code isMember}. */
@Getter
@Builder
public class ChatRoomResponse {
    private Long id;
    private String name;
    private String description;
    private RoomType type;
    private Long createdById;
    private String createdByUsername;
    private long memberCount;
    private boolean isMember;
    private LocalDateTime createdAt;
}
