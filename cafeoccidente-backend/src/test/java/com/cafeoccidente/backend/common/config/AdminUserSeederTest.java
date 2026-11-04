package com.cafeoccidente.backend.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cafeoccidente.backend.common.security.Roles;
import com.cafeoccidente.backend.purchases.shared.entity.Agency;
import com.cafeoccidente.backend.purchases.shared.repository.AgencyRepository;
import com.cafeoccidente.backend.users.entity.Role;
import com.cafeoccidente.backend.users.entity.User;
import com.cafeoccidente.backend.users.repository.RoleRepository;
import com.cafeoccidente.backend.users.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

/** El primer Administrador se crea con el rol ADMINISTRADOR del RF (V3). */
class AdminUserSeederTest {

    private final UserRepository users = mock(UserRepository.class);
    private final RoleRepository roles = mock(RoleRepository.class);
    private final AgencyRepository agencies = mock(AgencyRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final AdminUserSeeder seeder = new AdminUserSeeder(users, roles, agencies, encoder, "admin", "clave");

    @Test
    void createsTheAdministratorWithTheRfRole() {
        Role administrator = new Role();
        administrator.setName(Roles.ADMINISTRADOR);
        when(users.findByUsername("admin")).thenReturn(Optional.empty());
        when(roles.findByName(Roles.ADMINISTRADOR)).thenReturn(Optional.of(administrator));
        when(agencies.findByActiveTrue()).thenReturn(List.of(new Agency()));

        seeder.run(null);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue().getRole().getName()).isEqualTo(Roles.ADMINISTRADOR);
    }

    @Test
    void doesNothingWhenTheAdministratorExists() {
        when(users.findByUsername("admin")).thenReturn(Optional.of(new User()));

        seeder.run(null);

        verify(users, never()).save(any());
    }

    @Test
    void failsWithoutTheAdministratorRole() {
        when(users.findByUsername("admin")).thenReturn(Optional.empty());
        when(roles.findByName(Roles.ADMINISTRADOR)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> seeder.run(null)).isInstanceOf(IllegalStateException.class);
    }
}
