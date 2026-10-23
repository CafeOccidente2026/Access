package com.cafeoccidente.backend.common.security;

import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Id y rol del usuario autenticado. La agencia de trabajo la da CurrentAgencyProvider. */
@Component
public class SecurityUtils {

    public Long getCurrentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof SecurityUser securityUser) {
            return securityUser.getUserId();
        }
        throw new IllegalStateException("No hay un usuario autenticado en el contexto actual");
    }

    /** Id del usuario autenticado, o vacio en tareas del sistema (seeder, arranque) que corren sin sesion. */
    public Optional<Long> findCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof SecurityUser securityUser
                ? Optional.of(securityUser.getUserId())
                : Optional.empty();
    }

    public boolean isAdmin() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof SecurityUser securityUser)) {
            throw new IllegalStateException("No hay un usuario autenticado en el contexto actual");
        }
        return securityUser.getAuthorities().stream()
                .anyMatch(authority -> ("ROLE_" + Roles.ADMINISTRADOR).equals(authority.getAuthority()));
    }
}
