package com.cafeoccidente.backend.users.service;

import com.cafeoccidente.backend.users.dto.UserRequest;
import com.cafeoccidente.backend.users.dto.UserResponse;
import java.util.List;

public interface UserService {

    List<UserResponse> list();

    UserResponse create(UserRequest request);
}
