package com.chatapp.domain.message.entity;

import com.chatapp.common.entity.BaseEntity;
import com.chatapp.domain.chat.entity.ChatRoom;
import com.chatapp.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Persistent chat message. Supports soft-delete via {@link #deletedAt}: a non-null value
 * means the message is deleted and must be excluded from all queries with {@code AND deletedAt IS NULL}.
 * Hard deletes are not used so sender attribution and audit history are preserved.
 */
@Entity
@Table(name = "messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Message extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private ChatRoom room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageType type;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
