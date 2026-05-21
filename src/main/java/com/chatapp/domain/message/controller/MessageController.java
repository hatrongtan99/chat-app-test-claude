package com.chatapp.domain.message.controller;

import com.chatapp.common.response.ApiResponse;
import com.chatapp.common.response.PageResponse;
import com.chatapp.domain.message.dto.MessageResponse;
import com.chatapp.domain.message.dto.SendMessageRequest;
import com.chatapp.domain.message.service.MessageService;
import com.chatapp.domain.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * REST endpoints for per-room message operations: list, send, and soft-delete.
 * Requires room membership for all operations; ownership required for delete.
 */
@RestController
@RequestMapping("/api/rooms/{roomId}/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<MessageResponse>>> getMessages(
            @PathVariable Long roomId,
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                messageService.getRoomMessages(roomId, user.getId(), page, size)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<MessageResponse>> sendMessage(
            @PathVariable Long roomId,
            @AuthenticationPrincipal User user,
            @Valid @RequestBody SendMessageRequest request) {
        request.setRoomId(roomId);
        return ResponseEntity.ok(ApiResponse.success(
                messageService.sendMessage(roomId, user.getId(), request)));
    }

    @DeleteMapping("/{messageId}")
    public ResponseEntity<ApiResponse<Void>> deleteMessage(
            @PathVariable Long roomId,
            @PathVariable Long messageId,
            @AuthenticationPrincipal User user) {
        messageService.deleteMessage(messageId, user.getId());
        return ResponseEntity.ok(ApiResponse.success("Message deleted", null));
    }
}
