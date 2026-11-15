package com.eyup.kervan.user.mapper;

import com.eyup.kervan.user.dto.UserResponse;
import com.eyup.kervan.user.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface UserMapper {

    UserResponse toResponse(User user);
}
