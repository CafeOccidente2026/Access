package com.cafeoccidente.backend.users.service;

import com.cafeoccidente.backend.users.dto.LoginRequest;
import com.cafeoccidente.backend.users.dto.LoginResponse;
import com.cafeoccidente.backend.users.dto.RefreshRequest;

public interface AuthService {

    LoginResponse login(LoginRequest request);

    LoginResponse refresh(RefreshRequest request);
}
