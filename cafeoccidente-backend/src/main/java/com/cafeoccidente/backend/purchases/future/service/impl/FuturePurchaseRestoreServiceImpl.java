package com.cafeoccidente.backend.purchases.future.service.impl;

import com.cafeoccidente.backend.common.security.CurrentAgencyProvider;
import com.cafeoccidente.backend.history.service.ChangeHistoryService;
import com.cafeoccidente.backend.history.service.ChangeHistoryService.Action;
import com.cafeoccidente.backend.history.service.ChangeHistoryService.Entity;
import com.cafeoccidente.backend.purchases.drycoffee.repository.DryCoffeePurchaseRepository;
import com.cafeoccidente.backend.purchases.future.dto.FuturePurchaseRestoreRow;
import com.cafeoccidente.backend.purchases.future.entity.FuturePurchase;
import com.cafeoccidente.backend.purchases.future.repository.FuturePurchaseRepository;
import com.cafeoccidente.backend.purchases.future.service.FuturePurchaseRestoreService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FuturePurchaseRestoreServiceImpl implements FuturePurchaseRestoreService {

    private final FuturePurchaseRepository futurePurchaseRepository;
    private final DryCoffeePurchaseRepository dryCoffeePurchaseRepository;
    private final CurrentAgencyProvider currentAgencyProvider;
    private final ChangeHistoryService history;

    public FuturePurchaseRestoreServiceImpl(
            FuturePurchaseRepository futurePurchaseRepository,
            DryCoffeePurchaseRepository dryCoffeePurchaseRepository,
            CurrentAgencyProvider currentAgencyProvider,
            ChangeHistoryService history) {
        this.futurePurchaseRepository = futurePurchaseRepository;
        this.dryCoffeePurchaseRepository = dryCoffeePurchaseRepository;
        this.currentAgencyProvider = currentAgencyProvider;
        this.history = history;
    }

    @Override
    public List<FuturePurchaseRestoreRow> preview(String idNumber) {
        return futurePurchaseRepository
                .findByAgencyIdAndIdNumberOrderByIdAsc(currentAgencyProvider.currentAgencyId(), idNumber).stream()
                .map(this::row)
                .toList();
    }

    @Override
    @Transactional
    public List<FuturePurchaseRestoreRow> restore(String idNumber) {
        Long agencyId = currentAgencyProvider.currentAgencyId();
        // Bloqueadas: una factura que llegue mientras tanto espera y luego descuenta del saldo ya recalculado.
        return futurePurchaseRepository.lockByAgencyIdAndIdNumber(agencyId, idNumber).stream()
                .map(purchase -> {
                    FuturePurchaseRestoreRow row = row(purchase);
                    if (row.currentBalance().compareTo(row.recalculatedBalance()) != 0) {
                        purchase.setRemainingKg(row.recalculatedBalance());
                        futurePurchaseRepository.save(purchase);
                        history.record(Entity.COMPRA_ANUNCIADA, purchase.getId(), Action.RESTAURAR, agencyId,
                                Map.of("remainingKg", row.currentBalance().toPlainString()),
                                Map.of("remainingKg", row.recalculatedBalance().toPlainString()));
                    }
                    return row;
                })
                .toList();
    }

    private FuturePurchaseRestoreRow row(FuturePurchase purchase) {
        BigDecimal delivered = dryCoffeePurchaseRepository.sumNetKgOfValidByFuturePurchase(purchase.getId());
        return new FuturePurchaseRestoreRow(
                purchase.getId(),
                purchase.getAnnouncementNumber(),
                purchase.getAnnouncementDate(),
                purchase.getSpecialType(),
                purchase.getAnnouncedKg(),
                delivered,
                purchase.getRemainingKg(),
                purchase.getAnnouncedKg().subtract(delivered));
    }
}
