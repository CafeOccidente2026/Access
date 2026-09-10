package com.cafeoccidente.backend.users.service.impl;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.common.exception.ResourceNotFoundException;
import com.cafeoccidente.backend.purchases.shared.entity.Municipality;
import com.cafeoccidente.backend.purchases.shared.repository.MunicipalityRepository;
import com.cafeoccidente.backend.users.dto.UserRequest;
import com.cafeoccidente.backend.users.dto.UserResponse;
import com.cafeoccidente.backend.users.entity.Role;
import com.cafeoccidente.backend.users.entity.User;
import com.cafeoccidente.backend.users.mapper.UserMapper;
import com.cafeoccidente.backend.users.repository.RoleRepository;
import com.cafeoccidente.backend.users.repository.UserRepository;
import com.cafeoccidente.backend.users.service.UserService;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final MunicipalityRepository municipalityRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    public UserServiceImpl(
            UserRepository userRepository,
            RoleRepository roleRepository,
            MunicipalityRepository municipalityRepository,
            PasswordEncoder passwordEncoder,
            UserMapper userMapper) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.municipalityRepository = municipalityRepository;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
    }

    @Override
    public List<UserResponse> list() {
        return userRepository.findAll().stream().map(userMapper::toResponse).toList();
    }

    @Override
    public UserResponse create(UserRequest request) {
        if (userRepository.findByUsername(request.username()).isPresent()) {
            throw new BusinessRuleException("Ya existe un usuario con ese nombre de usuario");
        }
        Role role = roleRepository.findById(request.roleId())
                .orElseThrow(() -> new ResourceNotFoundException("Rol no encontrado"));
        Municipality municipality = municipalityRepository.findById(request.municipalityId())
                .orElseThrow(() -> new ResourceNotFoundException("Municipio no encontrado"));

        User user = new User();
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(role);
        user.setMunicipality(municipality);
        user.setActive(true);

        return userMapper.toResponse(userRepository.save(user));
    }
}
