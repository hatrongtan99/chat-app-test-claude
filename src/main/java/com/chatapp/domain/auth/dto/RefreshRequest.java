package com.chatapp.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** Payload carrying the refresh token for {@code POST /api/auth/refresh} and {@code POST /api/auth/logout}. */
@Getter
@Setter
public class RefreshRequest {

    @NotBlank
    private String refreshToken;
}
