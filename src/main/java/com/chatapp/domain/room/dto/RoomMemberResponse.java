package com.chatapp.domain.room.dto;

import com.chatapp.domain.room.entity.ChatRoomMember;
import com.chatapp.domain.room.entity.MemberRole;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RoomMemberResponse {

  private Long userId;
  private String username;
  private String displayName;
  private MemberRole role;
  private LocalDateTime joinedAt;

  public static RoomMemberResponse from(ChatRoomMember member) {
    return RoomMemberResponse.builder()
        .userId(member.getUser().getId())
        .username(member.getUser().getUsername())
        .displayName(
            member.getUser().getDisplayName() != null ? member.getUser().getDisplayName() : "")
        .role(member.getRole())
        .joinedAt(member.getJoinedAt())
        .build();
  }
}
