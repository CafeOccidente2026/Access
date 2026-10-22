package com.cafeoccidente.backend.common.security;

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

    public boolean isAdmin() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof SecurityUser securityUser)) {
            throw new IllegalStateException("No hay un usuario autenticado en el contexto actual");
        }
        return securityUser.getAuthorities().stream()
                .anyMatch(authority -> ("ROLE_" + Roles.ADMINISTRADOR).equals(authority.getAuthority()));
    }
}
