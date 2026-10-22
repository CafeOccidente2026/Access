package com.cafeoccidente.backend.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.cafeoccidente.backend.users.entity.Role;
import com.cafeoccidente.backend.users.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/** isAdmin() reconoce el rol ADMINISTRADOR del RF y ningun otro. */
class SecurityUtilsTest {

    private final SecurityUtils securityUtils = new SecurityUtils();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static void signIn(String roleName) {
        Role role = new Role();
        role.setName(roleName);
        User user = new User();
        user.setRole(role);
        SecurityUser principal = new SecurityUser(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @Test
    void administratorIsAdmin() {
        signIn(Roles.ADMINISTRADOR);
        assertThat(securityUtils.isAdmin()).isTrue();
    }

    @Test
    void userAndOperationsAreNotAdmin() {
        signIn(Roles.USUARIO);
        assertThat(securityUtils.isAdmin()).isFalse();
        signIn(Roles.OPERACIONES);
        assertThat(securityUtils.isAdmin()).isFalse();
    }
}
