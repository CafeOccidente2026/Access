package com.cafeoccidente.backend.users.service.impl;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.common.exception.ResourceNotFoundException;
import com.cafeoccidente.backend.common.security.Roles;
import com.cafeoccidente.backend.history.service.ChangeHistoryService;
import com.cafeoccidente.backend.history.service.ChangeHistoryService.Action;
import com.cafeoccidente.backend.history.service.ChangeHistoryService.Entity;
import com.cafeoccidente.backend.history.service.Snapshots;
import com.cafeoccidente.backend.purchases.shared.entity.Agency;
import com.cafeoccidente.backend.purchases.shared.entity.Grower;
import com.cafeoccidente.backend.purchases.shared.repository.AgencyRepository;
import com.cafeoccidente.backend.purchases.shared.repository.GrowerRepository;
import com.cafeoccidente.backend.users.dto.UserRequest;
import com.cafeoccidente.backend.users.dto.UserResponse;
import com.cafeoccidente.backend.users.dto.UserUpdateRequest;
import com.cafeoccidente.backend.users.entity.Role;
import com.cafeoccidente.backend.users.entity.User;
import com.cafeoccidente.backend.users.mapper.UserMapper;
import com.cafeoccidente.backend.users.repository.RoleRepository;
import com.cafeoccidente.backend.users.repository.UserRepository;
import com.cafeoccidente.backend.users.service.UserService;
import java.util.List;
import java.util.Map;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserServiceImpl implements UserService {

    static final String SELF_CHANGE = "No puede desactivarse ni quitarse el rol de Administrador a si mismo";
    static final String LAST_ADMIN = "Debe quedar al menos un Administrador activo";
    static final String DUPLICATE_ID_NUMBER = "Ya existe un usuario con esa cedula";
    static final String ID_NUMBER_FIXED = "La cedula del usuario no se puede cambiar";
    /** Tipo del tercero que se crea para un usuario: no asociado (fase 3, C5; confirmado por Operaciones). */
    static final String USER_GROWER_TYPE = "C";
    private static final String NO_DATA = "-";
    /** En el historial solo consta que la contrasena cambio, nunca su valor. */
    static final String PASSWORD_FIELD = "password";
    static final String PASSWORD_CHANGED = "cambiada";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AgencyRepository agencyRepository;
    private final GrowerRepository growerRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final ChangeHistoryService history;

    public UserServiceImpl(
            UserRepository userRepository,
            RoleRepository roleRepository,
            AgencyRepository agencyRepository,
            GrowerRepository growerRepository,
            PasswordEncoder passwordEncoder,
            UserMapper userMapper,
            ChangeHistoryService history) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.agencyRepository = agencyRepository;
        this.growerRepository = growerRepository;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
        this.history = history;
    }

    @Override
    public List<UserResponse> list() {
        return userRepository.findAll().stream().map(userMapper::toResponse).toList();
    }

    @Override
    @Transactional
    public UserResponse create(UserRequest request) {
        if (userRepository.findByUsername(request.username()).isPresent()) {
            throw new BusinessRuleException("Ya existe un usuario con ese nombre de usuario");
        }
        if (userRepository.existsByIdNumber(request.idNumber())) {
            throw new BusinessRuleException(DUPLICATE_ID_NUMBER);
        }
        User user = new User();
        user.setUsername(request.username());
        user.setIdNumber(request.idNumber());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(role(request.roleId()));
        user.setAgency(agency(request.agencyId()));
        user.setActive(true);
        ensureGrower(user);
        UserResponse created = userMapper.toResponse(userRepository.save(user));
        history.record(Entity.USUARIO, created.id(), Action.CREAR, created.agencyId(), null, snapshot(created));
        return created;
    }

    @Override
    @Transactional
    public UserResponse update(Long id, UserUpdateRequest request, Long currentUserId) {
        List<User> activeAdmins = userRepository.lockActiveByRole(Roles.ADMINISTRADOR);
        User user = userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        Role role = role(request.roleId());
        boolean staysActiveAdmin = request.active() && Roles.ADMINISTRADOR.equals(role.getName());
        if (!staysActiveAdmin && id.equals(currentUserId)) {
            throw new BusinessRuleException(SELF_CHANGE);
        }
        boolean isActiveAdmin = activeAdmins.stream().anyMatch(a -> a.getId().equals(id));
        if (!staysActiveAdmin && isActiveAdmin && activeAdmins.size() == 1) {
            throw new BusinessRuleException(LAST_ADMIN);
        }
        UserResponse before = userMapper.toResponse(user);
        user.setRole(role);
        user.setAgency(agency(request.agencyId()));
        user.setActive(request.active());
        boolean passwordChanged = request.password() != null && !request.password().isEmpty();
        if (passwordChanged) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        addIdNumber(user, request.idNumber());
        UserResponse after = userMapper.toResponse(userRepository.save(user));
        Map<String, String> afterValues = snapshot(after);
        if (passwordChanged) {
            afterValues.put(PASSWORD_FIELD, PASSWORD_CHANGED);
        }
        history.record(Entity.USUARIO, id, action(before, after), after.agencyId(), snapshot(before), afterValues);
        return after;
    }

    /** La accion que mejor describe el cambio: activar/desactivar, reasignar agencia o modificar. */
    private static Action action(UserResponse before, UserResponse after) {
        if (before.active() != after.active()) {
            return after.active() ? Action.ACTIVAR : Action.DESACTIVAR;
        }
        return before.agencyId().equals(after.agencyId()) ? Action.MODIFICAR : Action.REASIGNAR;
    }

    private static Map<String, String> snapshot(UserResponse user) {
        return Snapshots.of(user, "id", "roleId", "agencyId");
    }

    /** Los usuarios creados antes de V7 no tienen cedula: se agrega una vez y no se cambia. */
    private void addIdNumber(User user, String idNumber) {
        if (idNumber == null || idNumber.isEmpty() || idNumber.equals(user.getIdNumber())) {
            return;
        }
        if (user.getIdNumber() != null) {
            throw new BusinessRuleException(ID_NUMBER_FIXED);
        }
        if (userRepository.existsByIdNumber(idNumber)) {
            throw new BusinessRuleException(DUPLICATE_ID_NUMBER);
        }
        user.setIdNumber(idNumber);
        ensureGrower(user);
    }

    /**
     * RN-07.5 (fase 3, C5): si la cedula ya es un tercero (por ejemplo un asociado) esa fila no se toca;
     * si no, se crea con lo minimo, Tipo 'C' y la agencia del usuario.
     */
    private void ensureGrower(User user) {
        if (growerRepository.findByIdNumber(user.getIdNumber()).isPresent()) {
            return;
        }
        Grower grower = new Grower();
        grower.setIdNumber(user.getIdNumber());
        grower.setFirstName(user.getUsername());
        grower.setAddress(NO_DATA);
        grower.setPhone(NO_DATA);
        grower.setGrowerType(USER_GROWER_TYPE);
        grower.setAgency(user.getAgency());
        grower.setExported(false);
        growerRepository.save(grower);
    }

    private Role role(Long roleId) {
        return roleRepository.findById(roleId).orElseThrow(() -> new ResourceNotFoundException("Rol no encontrado"));
    }

    private Agency agency(Long agencyId) {
        return agencyRepository.findById(agencyId)
                .orElseThrow(() -> new ResourceNotFoundException("Agencia no encontrada"));
    }
}
