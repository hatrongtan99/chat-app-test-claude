package com.chatapp.domain.chat.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** Payload for {@code POST /api/rooms/{id}/invite} (ADMIN only). */
@Getter
@Setter
public class InviteRequest {

    @NotNull
    private Long userId;
}
