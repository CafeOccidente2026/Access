package com.cafeoccidente.backend.users.service;

import com.cafeoccidente.backend.users.dto.LoginRequest;
import com.cafeoccidente.backend.users.dto.LoginResponse;

public interface AuthService {

    /** S4: {@code ip} es la del cliente (detras de Caddy, la de X-Forwarded-For). */
    LoginResponse login(LoginRequest request, String ip);
}
