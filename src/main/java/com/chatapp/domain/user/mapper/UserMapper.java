package com.chatapp.domain.user.mapper;

import com.chatapp.domain.user.dto.UserResponse;
import com.chatapp.domain.user.entity.User;
import org.mapstruct.Mapper;

/** MapStruct mapper for {@link com.chatapp.domain.user.entity.User} → {@link com.chatapp.domain.user.dto.UserResponse}. */
@Mapper(componentModel = "spring")
public interface UserMapper {
    UserResponse toResponse(User user);
}
