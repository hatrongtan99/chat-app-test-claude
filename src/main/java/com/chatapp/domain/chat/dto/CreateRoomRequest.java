package com.chatapp.domain.chat.dto;

import com.chatapp.domain.chat.entity.RoomType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Payload for {@code POST /api/rooms}. {@code type} defaults to {@code PUBLIC}
 * if omitted.
 */
@Getter
@Setter
public class CreateRoomRequest {

    @NotBlank
    @Size(max = 100)
    private String name;

    @Size(max = 500)
    private String description;

    private RoomType type = RoomType.PUBLIC;
}
