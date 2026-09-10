package com.cafeoccidente.backend.common.security;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Unica responsabilidad: exponer el id del usuario autenticado a los servicios que lo necesiten. */
@Component
public class SecurityUtils {

    public Long getCurrentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof SecurityUser securityUser) {
            return securityUser.getUserId();
        }
        throw new IllegalStateException("No hay un usuario autenticado en el contexto actual");
    }
}
