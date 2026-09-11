package com.cafeoccidente.backend.common.config;

import com.cafeoccidente.backend.purchases.shared.entity.Agency;
import com.cafeoccidente.backend.purchases.shared.repository.AgencyRepository;
import com.cafeoccidente.backend.users.entity.Role;
import com.cafeoccidente.backend.users.entity.User;
import com.cafeoccidente.backend.users.repository.RoleRepository;
import com.cafeoccidente.backend.users.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Crea el usuario ADMIN inicial (desde variables de entorno) si todavia no existe. */
@Component
public class AdminUserSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AgencyRepository agencyRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminUsername;
    private final String adminPassword;

    public AdminUserSeeder(
            UserRepository userRepository,
            RoleRepository roleRepository,
            AgencyRepository agencyRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.admin.username}") String adminUsername,
            @Value("${app.admin.password}") String adminPassword) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.agencyRepository = agencyRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.findByUsername(adminUsername).isPresent()) {
            return;
        }
        Role adminRole = roleRepository.findByName("ADMIN")
                .orElseThrow(() -> new IllegalStateException("El rol ADMIN debe existir (ver Flyway V2)"));
        Agency agency = agencyRepository.findByActiveTrue().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Debe existir al menos una agencia (ver Flyway V7)"));

        User admin = new User();
        admin.setUsername(adminUsername);
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setRole(adminRole);
        admin.setAgency(agency);
        admin.setActive(true);
        userRepository.save(admin);
    }
}
