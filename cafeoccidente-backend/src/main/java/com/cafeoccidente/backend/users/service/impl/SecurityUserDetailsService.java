package com.cafeoccidente.backend.users.service.impl;

import com.cafeoccidente.backend.common.security.SecurityUser;
import com.cafeoccidente.backend.permissions.entity.Permission;
import com.cafeoccidente.backend.permissions.repository.PermissionRepository;
import com.cafeoccidente.backend.users.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Unica responsabilidad: cargar un User y adaptarlo al contrato de Spring Security. */
@Service
public class SecurityUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PermissionRepository permissionRepository;

    public SecurityUserDetailsService(UserRepository userRepository, PermissionRepository permissionRepository) {
        this.userRepository = userRepository;
        this.permissionRepository = permissionRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByUsername(username)
                .map(user -> new SecurityUser(user, permissionRepository.findByUserIdOrderByScreen(user.getId()).stream()
                        .map(Permission::getScreen).collect(Collectors.toSet())))
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + username));
    }
}
