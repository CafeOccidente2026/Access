package com.cafeoccidente.backend.permissions.service.impl;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.common.exception.ResourceNotFoundException;
import com.cafeoccidente.backend.common.security.Roles;
import com.cafeoccidente.backend.common.security.SecurityUtils;
import com.cafeoccidente.backend.history.service.ChangeHistoryService;
import com.cafeoccidente.backend.history.service.ChangeHistoryService.Action;
import com.cafeoccidente.backend.history.service.ChangeHistoryService.Entity;
import com.cafeoccidente.backend.permissions.dto.PermissionResponse;
import com.cafeoccidente.backend.permissions.entity.Permission;
import com.cafeoccidente.backend.permissions.entity.Screen;
import com.cafeoccidente.backend.permissions.repository.PermissionRepository;
import com.cafeoccidente.backend.permissions.service.PermissionService;
import com.cafeoccidente.backend.users.entity.User;
import com.cafeoccidente.backend.users.repository.UserRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PermissionServiceImpl implements PermissionService {

    /** En el Historial: "Pantalla: Anular Documento", con la accion Conceder o Retirar. */
    static final String SCREEN_FIELD = "screen";

    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final SecurityUtils securityUtils;
    private final ChangeHistoryService history;

    public PermissionServiceImpl(PermissionRepository permissionRepository, UserRepository userRepository,
            SecurityUtils securityUtils, ChangeHistoryService history) {
        this.permissionRepository = permissionRepository;
        this.userRepository = userRepository;
        this.securityUtils = securityUtils;
        this.history = history;
    }

    @Override
    public List<PermissionResponse> list(Long userId) {
        return permissionRepository.findByUserIdOrderByScreen(userId).stream()
                .map(p -> new PermissionResponse(p.getScreen(), p.getGrantedAt()))
                .toList();
    }

    @Override
    @Transactional
    public List<PermissionResponse> grant(Long userId, List<Screen> screens) {
        User user = findUser(userId);
        String role = user.getRole().getName();
        if (Roles.ADMINISTRADOR.equals(role)) {
            throw new BusinessRuleException("El Administrador ya tiene acceso a todas las pantallas");
        }
        if (Roles.OPERACIONES.equals(role) && screens.contains(Screen.ACTUALIZAR_ANUNCIOS)) {
            throw new BusinessRuleException("Operaciones ya tiene acceso a Actualizar Anuncios por su rol");
        }
        Long grantedBy = securityUtils.getCurrentUserId();
        screens.stream().distinct()
                .filter(screen -> permissionRepository.findByUserIdAndScreen(userId, screen).isEmpty())
                .forEach(screen -> {
                    Permission permission = new Permission();
                    permission.setUserId(userId);
                    permission.setScreen(screen);
                    permission.setGrantedBy(grantedBy);
                    permission.setGrantedAt(OffsetDateTime.now());
                    permissionRepository.save(permission);
                    record(user, Action.CONCEDER, screen);
                });
        return list(userId);
    }

    @Override
    @Transactional
    public void revoke(Long userId, Screen screen) {
        User user = findUser(userId);
        Permission permission = permissionRepository.findByUserIdAndScreen(userId, screen)
                .orElseThrow(() -> new ResourceNotFoundException("El usuario no tiene ese permiso"));
        permissionRepository.delete(permission);
        record(user, Action.RETIRAR, screen);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
    }

    private void record(User user, Action action, Screen screen) {
        history.record(Entity.PERMISO, user.getId(), action, user.getAgency().getId(), null,
                Map.of(SCREEN_FIELD, screen.getLabel()));
    }
}
