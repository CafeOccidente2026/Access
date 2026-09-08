package com.cafeoccidente.backend.users.service.impl;

import com.cafeoccidente.backend.common.security.JwtService;
import com.cafeoccidente.backend.users.dto.LoginRequest;
import com.cafeoccidente.backend.users.dto.LoginResponse;
import com.cafeoccidente.backend.users.dto.RefreshRequest;
import com.cafeoccidente.backend.users.entity.User;
import com.cafeoccidente.backend.users.repository.UserRepository;
import com.cafeoccidente.backend.users.service.AuthService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthServiceImpl implements AuthService {

    private static final String INVALID_CREDENTIALS_MESSAGE = "Usuario o contraseña incorrectos";
    private static final String INVALID_REFRESH_TOKEN_MESSAGE = "Refresh token invalido o expirado";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.username())
                .filter(u -> u.isActive())
                .orElseThrow(() -> new BadCredentialsException(INVALID_CREDENTIALS_MESSAGE));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException(INVALID_CREDENTIALS_MESSAGE);
        }
        return new LoginResponse(
                jwtService.generateAccessToken(user),
                jwtService.generateRefreshToken(user),
                user.getUsername(),
                user.getRole());
    }

    @Override
    public LoginResponse refresh(RefreshRequest request) {
        String refreshToken = request.refreshToken();
        if (!jwtService.isRefreshTokenValid(refreshToken)) {
            throw new BadCredentialsException(INVALID_REFRESH_TOKEN_MESSAGE);
        }
        User user = userRepository.findByUsername(jwtService.extractUsername(refreshToken))
                .filter(u -> u.isActive())
                .orElseThrow(() -> new BadCredentialsException(INVALID_REFRESH_TOKEN_MESSAGE));
        return new LoginResponse(
                jwtService.generateAccessToken(user),
                refreshToken,
                user.getUsername(),
                user.getRole());
    }
}
