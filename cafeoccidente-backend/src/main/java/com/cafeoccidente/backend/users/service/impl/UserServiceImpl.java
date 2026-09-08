package com.cafeoccidente.backend.users.service.impl;

import com.cafeoccidente.backend.common.exception.ApiException;
import com.cafeoccidente.backend.common.exception.ResourceNotFoundException;
import com.cafeoccidente.backend.users.dto.UserRequest;
import com.cafeoccidente.backend.users.dto.UserResponse;
import com.cafeoccidente.backend.users.entity.User;
import com.cafeoccidente.backend.users.repository.UserRepository;
import com.cafeoccidente.backend.users.service.UserService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public UserResponse createUser(UserRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new ApiException("El nombre de usuario ya existe", HttpStatus.CONFLICT);
        }
        User user = new User();
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName());
        user.setRole(request.role());
        user.setActive(true);
        return toResponse(userRepository.save(user));
    }

    @Override
    public List<UserResponse> listUsers() {
        return userRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Override
    public void deactivateUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        user.setActive(false);
        userRepository.save(user);
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(), user.getUsername(), user.getFullName(), user.getRole(), user.isActive(),
                user.getCreatedAt());
    }
}
