package com.cafeoccidente.backend.common.security;

import com.cafeoccidente.backend.permissions.entity.Screen;
import java.util.Optional;
import java.util.Set;
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

    /** Pantallas concedidas al usuario autenticado (RF-02), leidas de la BD en este request. */
    public Set<Screen> currentScreens() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return principal instanceof SecurityUser securityUser ? securityUser.getScreens() : Set.of();
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
