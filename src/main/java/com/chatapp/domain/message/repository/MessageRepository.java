package com.chatapp.domain.message.repository;

import com.chatapp.domain.message.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageRepository extends JpaRepository<Message, Long> {

    /**
     * Returns paginated messages for a room, newest first, excluding soft-deleted entries.
     *
     * <p>{@code JOIN FETCH m.sender} avoids N+1 queries when the caller maps each message
     * to a DTO that accesses sender fields. The {@code deletedAt IS NULL} condition is
     * explicit because {@code @Where} is deprecated in Hibernate 6 and not used in this project.
     */
    @Query("""
        SELECT m FROM Message m JOIN FETCH m.sender
        WHERE m.room.id = :roomId AND m.deletedAt IS NULL
        ORDER BY m.createdAt DESC
    """)
    Page<Message> findByRoomId(@Param("roomId") Long roomId, Pageable pageable);
}
