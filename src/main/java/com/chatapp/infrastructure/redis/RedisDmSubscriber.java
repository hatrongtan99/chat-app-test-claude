package com.chatapp.infrastructure.redis;

import com.chatapp.domain.directmessage.dto.DirectMessageEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis pub/sub subscriber that bridges the {@code chat:dm} channel to personal STOMP queues.
 *
 * <p>Receives a {@link DirectMessageEvent} from Redis (published by
 * {@link com.chatapp.domain.directmessage.service.DirectMessageService}) and delivers it to
 * both participants via {@code /user/queue/direct-messages}. Sending to both ensures the
 * sender sees the message echoed on all their connected tabs/devices.
 *
 * <p>In a multi-instance deployment each instance runs this subscriber. Only the instance
 * where a given user is connected will actually deliver to that user's STOMP session;
 * the other instances' calls are silently no-ops from the simple broker's perspective.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisDmSubscriber implements MessageListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            DirectMessageEvent event = objectMapper.readValue(message.getBody(), DirectMessageEvent.class);
            messagingTemplate.convertAndSendToUser(event.getRecipientUsername(), "/queue/direct-messages", event);
            messagingTemplate.convertAndSendToUser(event.getSenderUsername(), "/queue/direct-messages", event);
        } catch (Exception e) {
            log.error("Failed to process DM from Redis", e);
        }
    }
}
