package com.chatapp.domain.chat.entity;

import com.chatapp.common.entity.BaseEntity;
import com.chatapp.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

/**
 * Chat room entity. {@code type} controls access: {@code PUBLIC} rooms allow self-service join;
 * {@code PRIVATE} rooms require an explicit admin invite via
 * {@link com.chatapp.domain.chat.service.ChatRoomService#inviteUser}.
 */
@Entity
@Table(name = "chat_rooms")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatRoom extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoomType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;
}
