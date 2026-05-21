package com.chatapp.domain.directmessage.service;

import com.chatapp.common.exception.AppException;
import com.chatapp.common.exception.ErrorCode;
import com.chatapp.common.response.PageResponse;
import com.chatapp.domain.directmessage.dto.DirectMessageEvent;
import com.chatapp.domain.directmessage.dto.DirectMessageResponse;
import com.chatapp.domain.directmessage.entity.DirectMessage;
import com.chatapp.domain.directmessage.mapper.DirectMessageMapper;
import com.chatapp.domain.directmessage.repository.DirectMessageRepository;
import com.chatapp.domain.user.dto.UserResponse;
import com.chatapp.domain.user.entity.User;
import com.chatapp.domain.user.mapper.UserMapper;
import com.chatapp.domain.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DirectMessageService {

  private final DirectMessageRepository dmRepository;
  private final UserRepository userRepository;
  private final DirectMessageMapper dmMapper;
  private final UserMapper userMapper;

  @Qualifier("pubSubRedisTemplate")
  private final RedisTemplate<String, Object> pubSubRedisTemplate;

  public static final String DM_CHANNEL = "chat:dm";

  public PageResponse<DirectMessageResponse> getConversation(
      Long userId, Long partnerId, int page, int size) {
    if (!userRepository.existsById(partnerId)) {
      throw new AppException(ErrorCode.USER_NOT_FOUND);
    }
    Page<DirectMessageResponse> result =
        dmRepository
            .findConversation(userId, partnerId, PageRequest.of(page, size))
            .map(dmMapper::toResponse);
    return PageResponse.from(result);
  }

  /** Returns all users the current user has ever exchanged a DM with. */
  public List<UserResponse> getConversationPartners(Long userId) {
    List<Long> partnerIds = dmRepository.findDistinctPartnerIds(userId);
    return userRepository.findAllById(partnerIds).stream().map(userMapper::toResponse).toList();
  }

  @Transactional
  public DirectMessageResponse send(Long senderId, Long recipientId, String content) {
    if (senderId.equals(recipientId)) {
      throw new AppException(ErrorCode.CANNOT_MESSAGE_SELF);
    }
    User sender =
        userRepository
            .findById(senderId)
            .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    User recipient =
        userRepository
            .findById(recipientId)
            .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

    DirectMessage dm =
        DirectMessage.builder().sender(sender).recipient(recipient).content(content).build();
    dm = dmRepository.save(dm);

    DirectMessageResponse response = dmMapper.toResponse(dm);
    pubSubRedisTemplate.convertAndSend(
        DM_CHANNEL,
        DirectMessageEvent.builder()
            .eventType("DM_MESSAGE")
            .message(response)
            .senderUsername(sender.getUsername())
            .recipientUsername(recipient.getUsername())
            .timestamp(LocalDateTime.now())
            .build());
    return response;
  }
}
