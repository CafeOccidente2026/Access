package com.cafeoccidente.backend.users.service;

import com.cafeoccidente.backend.users.dto.LoginRequest;
import com.cafeoccidente.backend.users.dto.LoginResponse;

public interface AuthService {

    LoginResponse login(LoginRequest request);
}
