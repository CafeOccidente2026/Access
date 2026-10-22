package com.cafeoccidente.backend.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cafeoccidente.backend.purchases.shared.entity.Agency;
import com.cafeoccidente.backend.users.entity.Role;
import com.cafeoccidente.backend.users.entity.User;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/** Agencia de trabajo (RF-05.1, RN-01.3): el Usuario siempre la asignada; el Administrador la ultima que eligio. */
class CurrentAgencyProviderTest {

    private final CurrentAgencyProvider provider = new CurrentAgencyProvider();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static Agency agency(long id, boolean active) {
        Agency agency = new Agency();
        agency.setId(id);
        agency.setActive(active);
        return agency;
    }

    private static void signIn(String roleName, Agency assigned, Agency last) {
        Role role = new Role();
        role.setName(roleName);
        User user = new User();
        user.setRole(role);
        user.setAgency(assigned);
        user.setLastAgency(last);
        SecurityUser principal = new SecurityUser(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @Test
    void administratorWorksOnTheLastAgencyHeChose() {
        signIn(Roles.ADMINISTRADOR, agency(1, true), agency(2, true));
        assertThat(provider.currentAgencyId()).isEqualTo(2L);
    }

    @Test
    void administratorWithoutLastAgencyUsesHisAssignedOne() {
        signIn(Roles.ADMINISTRADOR, agency(1, true), null);
        assertThat(provider.currentAgencyId()).isEqualTo(1L);
    }

    @Test
    void administratorWhoseLastAgencyIsInactiveUsesHisAssignedOne() {
        signIn(Roles.ADMINISTRADOR, agency(1, true), agency(2, false));
        assertThat(provider.currentAgencyId()).isEqualTo(1L);
    }

    @Test
    void withoutAnAuthenticatedUserThereIsNoAgency() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("anonimo", null, List.of()));
        assertThatThrownBy(provider::currentAgencyId).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void userAlwaysWorksOnHisAssignedAgency() {
        signIn(Roles.USUARIO, agency(1, true), agency(2, true));
        assertThat(provider.currentAgencyId()).isEqualTo(1L);
    }
}
