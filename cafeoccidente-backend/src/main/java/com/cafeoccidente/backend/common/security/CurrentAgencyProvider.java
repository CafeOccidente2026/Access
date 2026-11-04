package com.cafeoccidente.backend.common.security;

import com.cafeoccidente.backend.purchases.shared.entity.Agency;
import com.cafeoccidente.backend.users.entity.User;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Agencia de trabajo del usuario autenticado (RF-05.1, RN-01.3). Es la unica fuente de la agencia en
 * las operaciones: el servidor nunca toma la agencia del request (salvo Registro de Control, RN-05.1.6).
 */
@Component
public class CurrentAgencyProvider {

    public Long currentAgencyId() {
        return workingAgency(currentUser()).getId();
    }

    /**
     * Usuario y Operaciones: su agencia asignada. Administrador: la ultima que eligio con el boton
     * Agencias; si no eligio o esa agencia quedo inactiva, su agencia asignada.
     */
    public static Agency workingAgency(User user) {
        Agency last = user.getLastAgency();
        boolean administrator = Roles.ADMINISTRADOR.equals(user.getRole().getName());
        return administrator && last != null && last.isActive() ? last : user.getAgency();
    }

    private static User currentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof SecurityUser securityUser) {
            return securityUser.getUser();
        }
        throw new IllegalStateException("No hay un usuario autenticado en el contexto actual");
    }
}
