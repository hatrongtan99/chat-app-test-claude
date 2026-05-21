package com.chatapp.domain.user.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/** Public user profile DTO. Does not expose the password hash. */
@Getter
@Builder
public class UserResponse {
    private Long id;
    private String username;
    private String email;
    private String displayName;
    private String avatarUrl;
    private LocalDateTime createdAt;
}
