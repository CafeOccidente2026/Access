package com.cafeoccidente.backend.users.service.impl;

import com.cafeoccidente.backend.common.security.JwtService;
import com.cafeoccidente.backend.common.security.SecurityUser;
import com.cafeoccidente.backend.users.dto.LoginRequest;
import com.cafeoccidente.backend.users.dto.LoginResponse;
import com.cafeoccidente.backend.users.service.AuthService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthServiceImpl(AuthenticationManager authenticationManager, JwtService jwtService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        var authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        SecurityUser user = (SecurityUser) authentication.getPrincipal();
        String token = jwtService.generateToken(user);
        String roleName = user.getAuthorities().iterator().next().getAuthority().replace("ROLE_", "");
        return new LoginResponse(token, user.getUsername(), roleName);
    }
}
