package com.chatapp.domain.room.entity;

import com.chatapp.domain.chat.entity.ChatRoom;
import com.chatapp.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Join table entity representing a user's membership in a chat room.
 * The {@code (room_id, user_id)} unique constraint prevents duplicate membership rows.
 * {@code joinedAt} is set automatically via {@link #onCreate()} and is never updated.
 */
@Entity
@Table(name = "chat_room_members",
        uniqueConstraints = @UniqueConstraint(columnNames = {"room_id", "user_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatRoomMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private ChatRoom room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemberRole role;

    @PrePersist
    protected void onCreate() {
        joinedAt = LocalDateTime.now();
    }
}
