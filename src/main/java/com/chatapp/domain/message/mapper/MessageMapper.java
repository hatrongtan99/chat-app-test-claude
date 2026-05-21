package com.chatapp.domain.message.mapper;

import com.chatapp.domain.message.dto.MessageResponse;
import com.chatapp.domain.message.entity.Message;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper for {@link com.chatapp.domain.message.entity.Message} → DTO conversions.
 * Flattens the nested {@code sender} and {@code room} associations into flat DTO fields.
 */
@Mapper(componentModel = "spring")
public interface MessageMapper {

    @Mapping(target = "roomId", source = "room.id")
    @Mapping(target = "senderId", source = "sender.id")
    @Mapping(target = "senderUsername", source = "sender.username")
    @Mapping(target = "senderDisplayName", source = "sender.displayName")
    MessageResponse toResponse(Message message);
}
