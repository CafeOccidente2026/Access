package com.cafeoccidente.backend.common.config;

import com.cafeoccidente.backend.purchases.shared.entity.Municipality;
import com.cafeoccidente.backend.purchases.shared.repository.MunicipalityRepository;
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
    private final MunicipalityRepository municipalityRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminUsername;
    private final String adminPassword;

    public AdminUserSeeder(
            UserRepository userRepository,
            RoleRepository roleRepository,
            MunicipalityRepository municipalityRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.admin.username}") String adminUsername,
            @Value("${app.admin.password}") String adminPassword) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.municipalityRepository = municipalityRepository;
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
        Municipality municipality = municipalityRepository.findByActiveTrue().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Debe existir al menos un municipio (ver Flyway V3)"));

        User admin = new User();
        admin.setUsername(adminUsername);
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setRole(adminRole);
        admin.setMunicipality(municipality);
        admin.setActive(true);
        userRepository.save(admin);
    }
}
