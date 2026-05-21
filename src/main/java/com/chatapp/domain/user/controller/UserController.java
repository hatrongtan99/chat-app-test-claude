package com.chatapp.domain.user.controller;

import com.chatapp.common.response.ApiResponse;
import com.chatapp.domain.user.dto.UpdateProfileRequest;
import com.chatapp.domain.user.dto.UserResponse;
import com.chatapp.domain.user.entity.User;
import com.chatapp.domain.user.service.UserService;
import com.chatapp.infrastructure.redis.PresenceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST endpoints for user profile management and online presence queries.
 * All endpoints require a valid Bearer JWT.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final PresenceService presenceService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getMe(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.success(userService.getProfile(user.getId())));
    }

    @PutMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> updateMe(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.success(userService.updateProfile(user.getId(), request)));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<UserResponse>>> search(@RequestParam String q) {
        return ResponseEntity.ok(ApiResponse.success(userService.searchUsers(q)));
    }

    @GetMapping("/{id}/online")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> isOnline(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(Map.of("online", presenceService.isOnline(id))));
    }
}
