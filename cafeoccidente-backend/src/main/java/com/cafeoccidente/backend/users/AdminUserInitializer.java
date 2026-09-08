package com.cafeoccidente.backend.users;

import com.cafeoccidente.backend.users.entity.Role;
import com.cafeoccidente.backend.users.entity.User;
import com.cafeoccidente.backend.users.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * No hay auto-registro, asi que el sistema necesita arrancar con un admin ya
 * creado. Si la tabla users esta vacia, crea uno a partir de ADMIN_USERNAME /
 * ADMIN_PASSWORD (variables de entorno) con el mismo hashing que cualquier
 * otro usuario.
 */
@Component
public class AdminUserInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminUsername;
    private final String adminPassword;

    public AdminUserInitializer(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${ADMIN_USERNAME:}") String adminUsername,
            @Value("${ADMIN_PASSWORD:}") String adminPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            return;
        }
        if (adminUsername.isBlank() || adminPassword.isBlank()) {
            throw new IllegalStateException(
                    "ADMIN_USERNAME y ADMIN_PASSWORD deben estar definidas para crear el primer administrador");
        }
        User admin = new User();
        admin.setUsername(adminUsername);
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setFullName("Administrador");
        admin.setRole(Role.ADMIN);
        admin.setActive(true);
        userRepository.save(admin);
    }
}
