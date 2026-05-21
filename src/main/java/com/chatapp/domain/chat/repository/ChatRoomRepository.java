package com.chatapp.domain.chat.repository;

import com.chatapp.domain.chat.entity.ChatRoom;
import com.chatapp.domain.chat.entity.RoomType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    List<ChatRoom> findByType(RoomType type);

    Page<ChatRoom> findByTypeAndNameContainingIgnoreCase(RoomType type, String name, Pageable pageable);

    /**
     * Returns all rooms the user belongs to by joining through the membership table.
     * Uses a cross-entity JPQL join (fully qualified entity name) because
     * {@link com.chatapp.domain.room.entity.ChatRoomMember} is in a different domain package
     * and has no direct association mapped on {@code ChatRoom}.
     */
    @Query("""
        SELECT r FROM ChatRoom r
        JOIN com.chatapp.domain.room.entity.ChatRoomMember m ON m.room = r
        WHERE m.user.id = :userId
    """)
    List<ChatRoom> findRoomsByUserId(@Param("userId") Long userId);
}
