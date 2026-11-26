package com.cafeoccidente.backend.annulmentlog.service.impl;

import com.cafeoccidente.backend.annulmentlog.dto.AnnulmentLogFilter;
import com.cafeoccidente.backend.annulmentlog.dto.AnnulmentLogPage;
import com.cafeoccidente.backend.annulmentlog.repository.AnnulmentLogRepository;
import com.cafeoccidente.backend.annulmentlog.repository.AnnulmentLogRepository.DocumentSnapshot;
import com.cafeoccidente.backend.annulmentlog.service.AnnulmentLogService;
import com.cafeoccidente.backend.annulmentlog.service.AnnulmentReason;
import com.cafeoccidente.backend.common.security.SecurityUtils;
import com.cafeoccidente.backend.purchases.annulment.repository.AnnulmentModule;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnnulmentLogServiceImpl implements AnnulmentLogService {

    private final AnnulmentLogRepository repository;
    private final SecurityUtils securityUtils;

    public AnnulmentLogServiceImpl(AnnulmentLogRepository repository, SecurityUtils securityUtils) {
        this.repository = repository;
        this.securityUtils = securityUtils;
    }

    /** MANDATORY: la fila se escribe en la misma transaccion que la anulacion; si la anulacion falla, no queda. */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordPurchase(AnnulmentModule module, Long purchaseId, String reason) {
        DocumentSnapshot snapshot = repository.purchaseSnapshot(module, purchaseId);
        repository.insertWeb(AnnulmentLogRepository.purchaseType(module), snapshot.number(), snapshot.agencyId(),
                purchaseId, securityUtils.getCurrentUserId(), AnnulmentReason.require(reason), snapshot.values());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordRemission(Long remissionId, String reason) {
        DocumentSnapshot snapshot = repository.remissionSnapshot(remissionId);
        repository.insertWeb(AnnulmentLogRepository.REMISSION_TYPE, snapshot.number(), snapshot.agencyId(), remissionId,
                securityUtils.getCurrentUserId(), AnnulmentReason.require(reason), snapshot.values());
    }

    @Override
    public AnnulmentLogPage search(AnnulmentLogFilter filter) {
        return new AnnulmentLogPage(repository.search(filter), repository.count(filter));
    }
}
