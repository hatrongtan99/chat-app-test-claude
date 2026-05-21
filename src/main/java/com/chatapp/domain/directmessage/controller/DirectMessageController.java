package com.chatapp.domain.directmessage.controller;

import com.chatapp.common.response.ApiResponse;
import com.chatapp.common.response.PageResponse;
import com.chatapp.domain.directmessage.dto.DirectMessageResponse;
import com.chatapp.domain.directmessage.dto.SendDirectMessageRequest;
import com.chatapp.domain.directmessage.service.DirectMessageService;
import com.chatapp.domain.user.dto.UserResponse;
import com.chatapp.domain.user.entity.User;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dm")
@RequiredArgsConstructor
public class DirectMessageController {

  private final DirectMessageService dmService;

  /** Returns all users the caller has exchanged DMs with. */
  @GetMapping("/conversations")
  public ResponseEntity<ApiResponse<List<UserResponse>>> getConversations(
      @AuthenticationPrincipal User currentUser) {
    return ResponseEntity.ok(
        ApiResponse.success(dmService.getConversationPartners(currentUser.getId())));
  }

  @GetMapping("/{partnerId}/messages")
  public ResponseEntity<ApiResponse<PageResponse<DirectMessageResponse>>> getConversation(
      @PathVariable Long partnerId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @AuthenticationPrincipal User currentUser) {
    return ResponseEntity.ok(
        ApiResponse.success(dmService.getConversation(currentUser.getId(), partnerId, page, size)));
  }

  @PostMapping("/{recipientId}")
  public ResponseEntity<ApiResponse<DirectMessageResponse>> send(
      @PathVariable Long recipientId,
      @Valid @RequestBody SendDirectMessageRequest request,
      @AuthenticationPrincipal User currentUser) {
    DirectMessageResponse response =
        dmService.send(currentUser.getId(), recipientId, request.getContent());
    return ResponseEntity.ok(ApiResponse.success(response));
  }
}
