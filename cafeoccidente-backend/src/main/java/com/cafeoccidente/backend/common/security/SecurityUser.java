package com.cafeoccidente.backend.common.security;

import com.cafeoccidente.backend.permissions.entity.Screen;
import com.cafeoccidente.backend.users.entity.User;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/** Adaptador entre la entidad User y el contrato de Spring Security. */
public class SecurityUser implements UserDetails {

    private final User user;
    private final Set<Screen> screens;

    public SecurityUser(User user) {
        this(user, Set.of());
    }

    /** {@code screens}: los permisos vigentes del usuario (RF-02), leidos en cada request. */
    public SecurityUser(User user, Set<Screen> screens) {
        this.user = user;
        this.screens = Set.copyOf(screens);
    }

    public Long getUserId() {
        return user.getId();
    }

    /** El usuario tal como se leyo de la BD en este request (rol, agencia asignada y ultima agencia). */
    public User getUser() {
        return user;
    }

    public Set<Screen> getScreens() {
        return screens;
    }

    /** ROLE_<rol> mas SCREEN_<pantalla> por cada permiso vigente (Roles.ANNUL, etc.). */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole().getName()));
        screens.forEach(screen -> authorities.add(new SimpleGrantedAuthority(Screen.AUTHORITY_PREFIX + screen.name())));
        return authorities;
    }

    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return user.getUsername();
    }

    @Override
    public boolean isEnabled() {
        return user.isActive();
    }
}
