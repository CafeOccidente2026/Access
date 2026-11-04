package com.cafeoccidente.backend.history.service.impl;

import com.cafeoccidente.backend.common.security.SecurityUtils;
import com.cafeoccidente.backend.history.dto.ChangeHistoryFilter;
import com.cafeoccidente.backend.history.dto.ChangeHistoryPage;
import com.cafeoccidente.backend.history.repository.ChangeHistoryRepository;
import com.cafeoccidente.backend.history.service.ChangeHistoryService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class ChangeHistoryServiceImpl implements ChangeHistoryService {

    private final ChangeHistoryRepository repository;
    private final SecurityUtils securityUtils;

    public ChangeHistoryServiceImpl(ChangeHistoryRepository repository, SecurityUtils securityUtils) {
        this.repository = repository;
        this.securityUtils = securityUtils;
    }

    @Override
    public void record(Entity entity, Long entityId, Action action, Long agencyId,
            Map<String, String> before, Map<String, String> after) {
        Optional<Long> userId = securityUtils.findCurrentUserId();
        if (userId.isEmpty()) {
            return; // tareas del sistema (AdminUserSeeder, migrate_eltambo.py): no hay quien firme el cambio
        }
        Map<String, String> changedBefore = before == null ? null : new LinkedHashMap<>();
        Map<String, String> changedAfter = new LinkedHashMap<>();
        after.forEach((field, value) -> {
            String old = before == null ? null : before.get(field);
            boolean changed = before == null ? value != null : !Objects.equals(old, value);
            if (changed) {
                changedAfter.put(field, value);
                if (changedBefore != null) {
                    changedBefore.put(field, old);
                }
            }
        });
        if (!changedAfter.isEmpty()) {
            repository.insert(userId.get(), entity.name(), entityId, action.name(), agencyId, changedBefore, changedAfter);
        }
    }

    @Override
    public ChangeHistoryPage search(ChangeHistoryFilter filter) {
        return new ChangeHistoryPage(repository.search(filter), repository.count(filter));
    }
}
