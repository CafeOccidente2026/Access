package com.cafeoccidente.backend.users.service.impl;

import com.cafeoccidente.backend.common.security.CurrentAgencyProvider;
import com.cafeoccidente.backend.common.security.JwtService;
import com.cafeoccidente.backend.common.security.LoginThrottle;
import com.cafeoccidente.backend.common.security.SecurityUser;
import com.cafeoccidente.backend.users.dto.LoginRequest;
import com.cafeoccidente.backend.purchases.shared.entity.Agency;
import com.cafeoccidente.backend.users.dto.LoginResponse;
import com.cafeoccidente.backend.users.service.AuthService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final LoginThrottle throttle;

    public AuthServiceImpl(AuthenticationManager authenticationManager, JwtService jwtService, LoginThrottle throttle) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.throttle = throttle;
    }

    @Override
    public LoginResponse login(LoginRequest request, String ip) {
        throttle.check(request.username(), ip);
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        } catch (BadCredentialsException e) {
            throttle.failed(request.username(), ip);
            throw e;
        }
        // Un usuario desactivado con la clave correcta no cuenta como fallo (sale por DisabledException).
        throttle.succeeded(request.username());
        SecurityUser user = (SecurityUser) authentication.getPrincipal();
        String token = jwtService.generateToken(user);
        String roleName = user.getAuthorities().iterator().next().getAuthority().replace("ROLE_", "");
        // RN-05.1.5: el Administrador entra con la ultima agencia que uso.
        Agency agency = CurrentAgencyProvider.workingAgency(user.getUser());
        return new LoginResponse(token, user.getUsername(), roleName, agency.getId(), agency.getName(),
                user.getScreens().stream().sorted().toList());
    }
}
