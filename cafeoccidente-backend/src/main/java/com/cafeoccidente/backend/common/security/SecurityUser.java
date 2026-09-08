package com.cafeoccidente.backend.common.security;

import com.cafeoccidente.backend.users.entity.Role;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Adaptador entre la identidad extraida del JWT y el contrato de Spring
 * Security. No se construye desde la base de datos en cada request (el JWT ya
 * trae username y rol), asi el filtro no necesita golpear la base por peticion.
 */
public class SecurityUser implements UserDetails {

    private final String username;
    private final Role role;

    public SecurityUser(String username, Role role) {
        this.username = username;
        this.role = role;
    }

    public Role getRole() {
        return role;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return username;
    }
}
