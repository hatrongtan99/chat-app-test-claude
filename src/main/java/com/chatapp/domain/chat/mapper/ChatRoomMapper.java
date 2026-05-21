package com.chatapp.domain.chat.mapper;

import com.chatapp.domain.chat.dto.ChatRoomResponse;
import com.chatapp.domain.chat.entity.ChatRoom;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper for {@link com.chatapp.domain.chat.entity.ChatRoom} → DTO conversions.
 * {@code memberCount} and {@code isMember} are computed at query time and ignored here.
 */
@Mapper(componentModel = "spring")
public interface ChatRoomMapper {

    @Mapping(target = "createdById", source = "createdBy.id")
    @Mapping(target = "createdByUsername", source = "createdBy.username")
    @Mapping(target = "memberCount", ignore = true)
    @Mapping(target = "isMember", ignore = true)
    ChatRoomResponse toResponse(ChatRoom room);
}
