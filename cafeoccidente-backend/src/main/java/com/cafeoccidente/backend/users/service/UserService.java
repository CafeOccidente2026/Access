package com.cafeoccidente.backend.users.service;

import com.cafeoccidente.backend.users.dto.UserRequest;
import com.cafeoccidente.backend.users.dto.UserResponse;
import com.cafeoccidente.backend.users.dto.UserUpdateRequest;
import java.util.List;

public interface UserService {

    List<UserResponse> list();

    /** HU-08 CA-1: el usuario queda en usuarios y en terceros (RN-07.5). */
    UserResponse create(UserRequest request);

    /** Fase 3, C4: el Administrador {@code currentUserId} edita al usuario {@code id}. */
    UserResponse update(Long id, UserUpdateRequest request, Long currentUserId);
}
