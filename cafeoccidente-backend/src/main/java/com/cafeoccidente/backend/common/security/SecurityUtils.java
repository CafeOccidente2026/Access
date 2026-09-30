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

    public Long getCurrentAgencyId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof SecurityUser securityUser) {
            return securityUser.getAgencyId();
        }
        throw new IllegalStateException("No hay un usuario autenticado en el contexto actual");
    }

    /** Agencia a consultar: ADMIN puede pedir cualquiera; USER siempre su agencia de sesion (el
     *  agencyId del parametro se ignora, asi no puede ver otra agencia cambiando la URL). */
    public Long resolveAgencyId(Long requestedAgencyId) {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof SecurityUser securityUser)) {
            throw new IllegalStateException("No hay un usuario autenticado en el contexto actual");
        }
        boolean admin = securityUser.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        return admin && requestedAgencyId != null ? requestedAgencyId : securityUser.getAgencyId();
    }
}
