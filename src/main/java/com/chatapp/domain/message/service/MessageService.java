package com.chatapp.domain.message.service;

import com.chatapp.common.exception.AppException;
import com.chatapp.common.exception.ErrorCode;
import com.chatapp.common.response.PageResponse;
import com.chatapp.domain.chat.entity.ChatRoom;
import com.chatapp.domain.chat.repository.ChatRoomRepository;
import com.chatapp.domain.message.dto.MessageResponse;
import com.chatapp.domain.message.dto.SendMessageRequest;
import com.chatapp.domain.message.dto.WebSocketMessage;
import com.chatapp.domain.message.entity.Message;
import com.chatapp.domain.message.entity.MessageType;
import com.chatapp.domain.message.mapper.MessageMapper;
import com.chatapp.domain.message.repository.MessageRepository;
import com.chatapp.domain.room.repository.ChatRoomMemberRepository;
import com.chatapp.domain.user.entity.User;
import com.chatapp.domain.user.repository.UserRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles message persistence and real-time fanout via Redis pub/sub.
 *
 * <p>Send flow: save to MySQL → publish {@link WebSocketMessage} JSON to the Redis channel {@code
 * chat:messages} → {@link com.chatapp.infrastructure.redis.RedisMessageSubscriber} receives it and
 * forwards to STOMP topic {@code /topic/rooms/{roomId}}.
 *
 * <p><strong>Transaction note:</strong> the Redis publish in {@link #publish} is outside the MySQL
 * transaction. A Redis failure after a successful DB commit will result in the message being
 * persisted but not delivered in real-time; clients can recover via REST polling.
 */
@Service
@RequiredArgsConstructor
public class MessageService {

  private final MessageRepository messageRepository;
  private final ChatRoomRepository chatRoomRepository;
  private final ChatRoomMemberRepository memberRepository;
  private final UserRepository userRepository;
  private final MessageMapper messageMapper;

  // Injected by qualifier to avoid conflict with the auto-configured StringRedisTemplate
  @Qualifier("pubSubRedisTemplate")
  private final RedisTemplate<String, Object> pubSubRedisTemplate;

  private static final String CHAT_CHANNEL = "chat:messages";

  /**
   * Returns paginated messages for a room, newest first, excluding soft-deleted entries.
   *
   * @throws AppException {@code ROOM_NOT_MEMBER} if the caller is not a room member
   */
  @Transactional(readOnly = true)
  public PageResponse<MessageResponse> getRoomMessages(
      Long roomId, Long userId, int page, int size) {
    if (!memberRepository.existsByRoomIdAndUserId(roomId, userId)) {
      throw new AppException(ErrorCode.ROOM_NOT_MEMBER);
    }
    Page<MessageResponse> result =
        messageRepository
            .findByRoomId(roomId, PageRequest.of(page, size))
            .map(messageMapper::toResponse);
    return PageResponse.from(result);
  }

  /**
   * Persists a message and publishes a {@code CHAT_MESSAGE} event to Redis. Defaults to {@code
   * MessageType.TEXT} if {@code request.type} is null.
   *
   * @throws AppException {@code ROOM_NOT_MEMBER} if the sender is not a room member
   */
  @Transactional
  public MessageResponse sendMessage(Long roomId, Long senderId, SendMessageRequest request) {
    if (!memberRepository.existsByRoomIdAndUserId(roomId, senderId)) {
      throw new AppException(ErrorCode.ROOM_NOT_MEMBER);
    }
    ChatRoom room =
        chatRoomRepository
            .findById(roomId)
            .orElseThrow(() -> new AppException(ErrorCode.ROOM_NOT_FOUND));
    User sender =
        userRepository
            .findById(senderId)
            .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

    Message message =
        Message.builder()
            .room(room)
            .sender(sender)
            .content(request.getContent())
            .type(request.getType() != null ? request.getType() : MessageType.TEXT)
            .build();
    message = messageRepository.save(message);

    MessageResponse response = messageMapper.toResponse(message);
    publish(
        WebSocketMessage.builder()
            .eventType("CHAT_MESSAGE")
            .roomId(roomId)
            .message(response)
            .userId(senderId)
            .username(sender.getUsername())
            .timestamp(LocalDateTime.now())
            .build());
    return response;
  }

  /**
   * Soft-deletes a message by setting {@code deletedAt}. Only the original sender may delete.
   *
   * @throws AppException {@code FORBIDDEN} if the caller is not the message sender
   */
  @Transactional
  public void deleteMessage(Long messageId, Long userId) {
    Message message =
        messageRepository
            .findById(messageId)
            .orElseThrow(() -> new AppException(ErrorCode.MESSAGE_NOT_FOUND));
    if (!message.getSender().getId().equals(userId)) {
      throw new AppException(ErrorCode.FORBIDDEN);
    }
    message.setDeletedAt(LocalDateTime.now());
    messageRepository.save(message);
  }

  /**
   * Persists and broadcasts a {@code SYSTEM} message (e.g., "Room X has been created"). Falls back
   * to the room creator as sender if no system user (id=1) exists.
   */
  @Transactional
  public void sendSystemMessage(Long roomId, String content) {
    ChatRoom room =
        chatRoomRepository
            .findById(roomId)
            .orElseThrow(() -> new AppException(ErrorCode.ROOM_NOT_FOUND));
    // Attempt to use a dedicated system user (id=1); fall back to room creator if absent.
    User system = userRepository.findById(1L).orElse(null);

    Message message =
        Message.builder()
            .room(room)
            .sender(system != null ? system : room.getCreatedBy())
            .content(content)
            .type(MessageType.SYSTEM)
            .build();
    message = messageRepository.save(message);

    MessageResponse response = messageMapper.toResponse(message);
    publish(
        WebSocketMessage.builder()
            .eventType("SYSTEM_MESSAGE")
            .roomId(roomId)
            .message(response)
            .timestamp(LocalDateTime.now())
            .build());
  }

  /** Publishes a WebSocket event to the shared Redis pub/sub channel for cross-instance fanout. */
  private void publish(WebSocketMessage wsMessage) {
    pubSubRedisTemplate.convertAndSend(CHAT_CHANNEL, wsMessage);
  }
}
