package com.chatapp.domain.directmessage.mapper;

import com.chatapp.domain.directmessage.dto.DirectMessageResponse;
import com.chatapp.domain.directmessage.entity.DirectMessage;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface DirectMessageMapper {

    @Mapping(target = "senderId",          source = "sender.id")
    @Mapping(target = "senderUsername",    source = "sender.username")
    @Mapping(target = "senderDisplayName", source = "sender.displayName")
    @Mapping(target = "recipientId",       source = "recipient.id")
    @Mapping(target = "recipientUsername", source = "recipient.username")
    DirectMessageResponse toResponse(DirectMessage dm);
}
