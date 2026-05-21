package com.chatapp.domain.chat.controller;

import com.chatapp.common.response.ApiResponse;
import com.chatapp.domain.chat.dto.ChatRoomResponse;
import com.chatapp.domain.chat.dto.CreateRoomRequest;
import com.chatapp.domain.chat.dto.InviteRequest;
import com.chatapp.domain.chat.service.ChatRoomService;
import com.chatapp.domain.message.service.MessageService;
import com.chatapp.domain.room.dto.RoomMemberResponse;
import com.chatapp.domain.user.entity.User;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class ChatRoomController {

  private final ChatRoomService chatRoomService;
  private final MessageService messageService;

  @GetMapping
  public ResponseEntity<ApiResponse<List<ChatRoomResponse>>> getPublicRooms(
      @AuthenticationPrincipal User user) {
    return ResponseEntity.ok(ApiResponse.success(chatRoomService.getPublicRooms(user.getId())));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<ChatRoomResponse>> createRoom(
      @AuthenticationPrincipal User user, @Valid @RequestBody CreateRoomRequest request) {
    ChatRoomResponse room = chatRoomService.createRoom(user.getId(), request);
    messageService.sendSystemMessage(
        room.getId(), "Room \"" + room.getName() + "\" has been created.");
    return ResponseEntity.ok(ApiResponse.success(room));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<ChatRoomResponse>> getRoom(
      @PathVariable Long id, @AuthenticationPrincipal User user) {
    return ResponseEntity.ok(ApiResponse.success(chatRoomService.getRoom(id, user.getId())));
  }

  @PostMapping("/{id}/join")
  public ResponseEntity<ApiResponse<ChatRoomResponse>> joinRoom(
      @PathVariable Long id, @AuthenticationPrincipal User user) {
    return ResponseEntity.ok(ApiResponse.success(chatRoomService.joinRoom(id, user.getId())));
  }

  @DeleteMapping("/{id}/leave")
  public ResponseEntity<ApiResponse<Void>> leaveRoom(
      @PathVariable Long id, @AuthenticationPrincipal User user) {
    chatRoomService.leaveRoom(id, user.getId());
    return ResponseEntity.ok(ApiResponse.success("Left room", null));
  }

  @GetMapping("/{id}/members")
  public ResponseEntity<ApiResponse<List<RoomMemberResponse>>> getMembers(
      @PathVariable Long id, @AuthenticationPrincipal User user) {
    List<RoomMemberResponse> result =
        chatRoomService.getRoomMembers(id, user.getId()).stream()
            .map(RoomMemberResponse::from)
            .toList();
    return ResponseEntity.ok(ApiResponse.success(result));
  }

  @GetMapping("/my")
  public ResponseEntity<ApiResponse<List<ChatRoomResponse>>> getMyRooms(
      @AuthenticationPrincipal User user) {
    return ResponseEntity.ok(ApiResponse.success(chatRoomService.getMyRooms(user.getId())));
  }

  @PostMapping("/{id}/invite")
  public ResponseEntity<ApiResponse<Void>> inviteUser(
      @PathVariable Long id,
      @AuthenticationPrincipal User user,
      @Valid @RequestBody InviteRequest request) {
    chatRoomService.inviteUser(id, user.getId(), request.getUserId());
    return ResponseEntity.ok(ApiResponse.success("User invited successfully", null));
  }
}
