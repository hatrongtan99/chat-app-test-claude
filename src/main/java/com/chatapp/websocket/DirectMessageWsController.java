package com.chatapp.websocket;

import com.chatapp.domain.directmessage.dto.DirectMessageSendRequest;
import com.chatapp.domain.directmessage.service.DirectMessageService;
import com.chatapp.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * STOMP {@code @MessageMapping} controller for direct messages.
 *
 * <p>Clients send to {@code /app/dm.send}; the message is persisted and a
 * {@code DM_MESSAGE} event is published to Redis, which fans it out to both
 * participants' personal queues at {@code /user/queue/direct-messages}.
 */
@Controller
@RequiredArgsConstructor
public class DirectMessageWsController {

    private final DirectMessageService dmService;

    @MessageMapping("/dm.send")
    public void handleDirectMessage(@Payload DirectMessageSendRequest request, Principal principal) {
        if (principal == null) return;
        User user = (User) ((UsernamePasswordAuthenticationToken) principal).getPrincipal();
        dmService.send(user.getId(), request.getRecipientId(), request.getContent());
    }
}
