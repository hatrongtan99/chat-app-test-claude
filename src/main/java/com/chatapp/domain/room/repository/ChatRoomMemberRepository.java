package com.chatapp.domain.room.repository;

import com.chatapp.domain.room.entity.ChatRoomMember;
import com.chatapp.domain.room.entity.MemberRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Repository for the {@code chat_room_members} join table, which tracks room membership and roles. */
public interface ChatRoomMemberRepository extends JpaRepository<ChatRoomMember, Long> {
    Optional<ChatRoomMember> findByRoomIdAndUserId(Long roomId, Long userId);
    boolean existsByRoomIdAndUserId(Long roomId, Long userId);
    long countByRoomId(Long roomId);
    void deleteByRoomIdAndUserId(Long roomId, Long userId);
    List<ChatRoomMember> findByRoomId(Long roomId);
    /** Used for ADMIN-only operations: returns non-empty only if the user holds the {@code ADMIN} role in this room. */
    Optional<ChatRoomMember> findByRoomIdAndUserIdAndRole(Long roomId, Long userId, MemberRole role);
}
