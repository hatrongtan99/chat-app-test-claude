package com.chatapp.domain.user.service;

import com.chatapp.common.exception.AppException;
import com.chatapp.common.exception.ErrorCode;
import com.chatapp.domain.user.dto.UpdateProfileRequest;
import com.chatapp.domain.user.dto.UserResponse;
import com.chatapp.domain.user.entity.User;
import com.chatapp.domain.user.mapper.UserMapper;
import com.chatapp.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Domain service for user profile reads, updates, and search.
 * Does not handle authentication — see {@link com.chatapp.domain.auth.service.AuthService}.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    public UserResponse getProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        return userMapper.toResponse(user);
    }

    /**
     * Partially updates the user's profile. Only non-null fields in the request are applied.
     */
    @Transactional
    public UserResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        if (request.getDisplayName() != null) user.setDisplayName(request.getDisplayName());
        if (request.getAvatarUrl() != null) user.setAvatarUrl(request.getAvatarUrl());
        return userMapper.toResponse(userRepository.save(user));
    }

    public List<UserResponse> searchUsers(String query) {
        return userRepository
                .findByUsernameContainingIgnoreCaseOrDisplayNameContainingIgnoreCase(query, query)
                .stream()
                .map(userMapper::toResponse)
                .toList();
    }
}
