package com.cafeoccidente.backend.users.service.impl;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.common.exception.ResourceNotFoundException;
import com.cafeoccidente.backend.common.security.CurrentAgencyProvider;
import com.cafeoccidente.backend.common.security.SecurityUtils;
import com.cafeoccidente.backend.permissions.entity.Screen;
import com.cafeoccidente.backend.purchases.shared.entity.Agency;
import com.cafeoccidente.backend.purchases.shared.repository.AgencyRepository;
import com.cafeoccidente.backend.users.dto.MeResponse;
import com.cafeoccidente.backend.users.entity.User;
import com.cafeoccidente.backend.users.repository.UserRepository;
import com.cafeoccidente.backend.users.service.WorkingAgencyService;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkingAgencyServiceImpl implements WorkingAgencyService {

    private final UserRepository userRepository;
    private final AgencyRepository agencyRepository;
    private final SecurityUtils securityUtils;

    public WorkingAgencyServiceImpl(
            UserRepository userRepository, AgencyRepository agencyRepository, SecurityUtils securityUtils) {
        this.userRepository = userRepository;
        this.agencyRepository = agencyRepository;
        this.securityUtils = securityUtils;
    }

    @Override
    public MeResponse me() {
        return toResponse(currentUser(), securityUtils.currentScreens());
    }

    /** RN-05.1.2 y RN-05.1.5: queda como agencia de trabajo y se recuerda para el proximo inicio de sesion. */
    @Override
    @Transactional
    public MeResponse choose(Long agencyId) {
        Agency agency = agencyRepository.findById(agencyId)
                .orElseThrow(() -> new ResourceNotFoundException("Agencia no encontrada"));
        if (!agency.isActive()) {
            throw new BusinessRuleException("La agencia " + agency.getName() + " esta inactiva");
        }
        User user = currentUser();
        user.setLastAgency(agency);
        return toResponse(userRepository.save(user), securityUtils.currentScreens());
    }

    private User currentUser() {
        return userRepository.findById(securityUtils.getCurrentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
    }

    private static MeResponse toResponse(User user, Set<Screen> screens) {
        Agency agency = CurrentAgencyProvider.workingAgency(user);
        return new MeResponse(user.getUsername(), user.getRole().getName(), agency.getId(), agency.getName(),
                screens.stream().sorted().toList());
    }
}
