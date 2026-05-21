package com.chatapp.domain.auth.dto;

import lombok.Builder;
import lombok.Getter;

/** Token pair returned on successful login, registration, or token refresh. */
@Getter
@Builder
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private long accessExpiresIn;
}
