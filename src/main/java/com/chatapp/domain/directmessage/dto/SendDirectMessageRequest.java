package com.chatapp.domain.directmessage.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** REST payload for sending a direct message. Recipient is supplied as a path variable. */
@Getter
@Setter
public class SendDirectMessageRequest {

    @NotBlank
    private String content;
}
