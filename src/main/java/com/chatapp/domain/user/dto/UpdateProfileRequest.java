package com.chatapp.domain.user.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Partial-update payload for {@code PUT /api/users/me}. Null fields are ignored
 * (no-op).
 */
@Getter
@Setter
public class UpdateProfileRequest {

    @Size(max = 100)
    private String displayName;

    @Size(max = 500)
    private String avatarUrl;
}
