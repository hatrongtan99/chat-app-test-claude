package com.chatapp.domain.directmessage.repository;

import com.chatapp.domain.directmessage.entity.DirectMessage;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DirectMessageRepository extends JpaRepository<DirectMessage, Long> {

  /**
   * Returns paginated messages in the conversation between two users, newest first. JOIN FETCH
   * avoids N+1 queries for sender and recipient.
   */
  @Query(
      """
      SELECT dm FROM DirectMessage dm
      JOIN FETCH dm.sender
      JOIN FETCH dm.recipient
      WHERE (dm.sender.id = :userId AND dm.recipient.id = :partnerId)
         OR (dm.sender.id = :partnerId AND dm.recipient.id = :userId)
      ORDER BY dm.createdAt DESC
      """)
  Page<DirectMessage> findConversation(
      @Param("userId") Long userId, @Param("partnerId") Long partnerId, Pageable pageable);

  /** Returns IDs of all distinct users this user has ever exchanged a DM with. */
  @Query(
      value =
          """
          SELECT DISTINCT IF(sender_id = :userId, recipient_id, sender_id)
          FROM direct_messages
          WHERE sender_id = :userId OR recipient_id = :userId
          """,
      nativeQuery = true)
  List<Long> findDistinctPartnerIds(@Param("userId") Long userId);
}
