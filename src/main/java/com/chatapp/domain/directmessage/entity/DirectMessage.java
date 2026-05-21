package com.chatapp.domain.directmessage.entity;

import com.chatapp.common.entity.BaseEntity;
import com.chatapp.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

/**
 * Persistent direct message between two users. Unlike room messages there is no
 * soft-delete in V1 — senders cannot retract DMs.
 */
@Entity
@Table(name = "direct_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DirectMessage extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;
}