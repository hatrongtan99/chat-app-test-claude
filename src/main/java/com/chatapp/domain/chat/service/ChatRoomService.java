package com.chatapp.domain.chat.service;

import com.chatapp.common.exception.AppException;
import com.chatapp.common.exception.ErrorCode;
import com.chatapp.domain.chat.dto.ChatRoomResponse;
import com.chatapp.domain.chat.dto.CreateRoomRequest;
import com.chatapp.domain.chat.entity.ChatRoom;
import com.chatapp.domain.chat.entity.RoomType;
import com.chatapp.domain.chat.repository.ChatRoomRepository;
import com.chatapp.domain.room.entity.ChatRoomMember;
import com.chatapp.domain.room.entity.MemberRole;
import com.chatapp.domain.room.repository.ChatRoomMemberRepository;
import com.chatapp.domain.user.entity.User;
import com.chatapp.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Domain service for chat room lifecycle: create, join, leave, invite, and query operations.
 *
 * <p>Business rules enforced here:
 * <ul>
 *   <li>Room creator is automatically assigned the {@code ADMIN} role.</li>
 *   <li>Private rooms ({@code type=PRIVATE}) cannot be joined via self-service; an ADMIN must invite.</li>
 *   <li>Member count is computed on each request — no denormalized counter is maintained.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ChatRoomService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository memberRepository;
    private final UserRepository userRepository;

    /**
     * Creates a new room and adds the creator as {@code ADMIN}.
     * If {@code request.type} is null, defaults to {@code PUBLIC}.
     *
     * @throws AppException {@code USER_NOT_FOUND} if the creator no longer exists
     */
    @Transactional
    public ChatRoomResponse createRoom(Long creatorId, CreateRoomRequest request) {
        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        ChatRoom room = ChatRoom.builder()
                .name(request.getName())
                .description(request.getDescription())
                .type(request.getType() != null ? request.getType() : RoomType.PUBLIC)
                .createdBy(creator)
                .build();
        room = chatRoomRepository.save(room);

        ChatRoomMember member = ChatRoomMember.builder()
                .room(room)
                .user(creator)
                .role(MemberRole.ADMIN)
                .build();
        memberRepository.save(member);

        return buildResponse(room, 1L, true);
    }

    @Transactional(readOnly = true)
    public List<ChatRoomResponse> getPublicRooms(Long currentUserId) {
        return chatRoomRepository.findByType(RoomType.PUBLIC).stream()
                .map(room -> buildResponse(room,
                        memberRepository.countByRoomId(room.getId()),
                        memberRepository.existsByRoomIdAndUserId(room.getId(), currentUserId)))
                .toList();
    }

    @Transactional(readOnly = true)
    public ChatRoomResponse getRoom(Long roomId, Long currentUserId) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new AppException(ErrorCode.ROOM_NOT_FOUND));
        return buildResponse(room,
                memberRepository.countByRoomId(roomId),
                memberRepository.existsByRoomIdAndUserId(roomId, currentUserId));
    }

    /**
     * Adds the calling user as a {@code MEMBER} of a public room via self-service join.
     *
     * @throws AppException {@code FORBIDDEN} if the room is {@code PRIVATE} (use invite instead)
     * @throws AppException {@code ALREADY_MEMBER} if the user is already in the room
     */
    @Transactional
    public ChatRoomResponse joinRoom(Long roomId, Long userId) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new AppException(ErrorCode.ROOM_NOT_FOUND));
        // Self-service join is blocked for private rooms; membership requires an explicit admin invite.
        if (room.getType() == RoomType.PRIVATE) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }
        if (memberRepository.existsByRoomIdAndUserId(roomId, userId)) {
            throw new AppException(ErrorCode.ALREADY_MEMBER);
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        memberRepository.save(ChatRoomMember.builder()
                .room(room).user(user).role(MemberRole.MEMBER).build());
        return buildResponse(room, memberRepository.countByRoomId(roomId), true);
    }

    @Transactional
    public void leaveRoom(Long roomId, Long userId) {
        if (!memberRepository.existsByRoomIdAndUserId(roomId, userId)) {
            throw new AppException(ErrorCode.ROOM_NOT_MEMBER);
        }
        memberRepository.deleteByRoomIdAndUserId(roomId, userId);
    }

    @Transactional(readOnly = true)
    public List<ChatRoomResponse> getMyRooms(Long userId) {
        return chatRoomRepository.findRoomsByUserId(userId).stream()
                .map(room -> buildResponse(room,
                        memberRepository.countByRoomId(room.getId()),
                        true))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ChatRoomMember> getRoomMembers(Long roomId, Long requesterId) {
        if (!memberRepository.existsByRoomIdAndUserId(roomId, requesterId)) {
            throw new AppException(ErrorCode.ROOM_NOT_MEMBER);
        }
        return memberRepository.findByRoomId(roomId);
    }

    /**
     * Adds {@code targetUserId} to the room. Caller must be an {@code ADMIN}.
     * The only way to add members to a {@code PRIVATE} room.
     *
     * @throws AppException {@code FORBIDDEN} if the caller is not an ADMIN of this room
     * @throws AppException {@code ALREADY_MEMBER} if the target is already a member
     */
    @Transactional
    public void inviteUser(Long roomId, Long inviterUserId, Long targetUserId) {
        memberRepository.findByRoomIdAndUserIdAndRole(roomId, inviterUserId, MemberRole.ADMIN)
                .orElseThrow(() -> new AppException(ErrorCode.FORBIDDEN));

        if (memberRepository.existsByRoomIdAndUserId(roomId, targetUserId)) {
            throw new AppException(ErrorCode.ALREADY_MEMBER);
        }
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new AppException(ErrorCode.ROOM_NOT_FOUND));
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        memberRepository.save(ChatRoomMember.builder()
                .room(room).user(target).role(MemberRole.MEMBER).build());
    }

    private ChatRoomResponse buildResponse(ChatRoom room, long memberCount, boolean isMember) {
        return ChatRoomResponse.builder()
                .id(room.getId())
                .name(room.getName())
                .description(room.getDescription())
                .type(room.getType())
                .createdById(room.getCreatedBy().getId())
                .createdByUsername(room.getCreatedBy().getUsername())
                .memberCount(memberCount)
                .isMember(isMember)
                .createdAt(room.getCreatedAt())
                .build();
    }
}
