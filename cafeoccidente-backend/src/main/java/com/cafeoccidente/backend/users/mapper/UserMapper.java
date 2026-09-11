package com.cafeoccidente.backend.users.mapper;

import com.cafeoccidente.backend.users.dto.UserResponse;
import com.cafeoccidente.backend.users.entity.User;
import org.springframework.stereotype.Component;

/** Sin MapStruct en el proyecto: mapeo manual, unica responsabilidad de traducir entidad <-> DTO. */
@Component
public class UserMapper {

    public UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.isActive(),
                user.getRole().getId(),
                user.getRole().getName(),
                user.getAgency().getId(),
                user.getAgency().getName());
    }
}
