package com.cafeoccidente.backend.purchases.report.service.impl;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.common.security.CurrentAgencyProvider;
import com.cafeoccidente.backend.purchases.report.dto.PurchaseReportRow;
import com.cafeoccidente.backend.purchases.report.repository.PurchaseReportRepository;
import com.cafeoccidente.backend.purchases.report.service.PurchaseReportService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PurchaseReportServiceImpl implements PurchaseReportService {

    static final String FROM_AFTER_TO = "La fecha Desde no puede ser posterior a la fecha Hasta";

    private final PurchaseReportRepository repository;
    private final CurrentAgencyProvider currentAgencyProvider;

    public PurchaseReportServiceImpl(PurchaseReportRepository repository, CurrentAgencyProvider currentAgencyProvider) {
        this.repository = repository;
        this.currentAgencyProvider = currentAgencyProvider;
    }

    @Override
    public List<PurchaseReportRow> byDate(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new BusinessRuleException(FROM_AFTER_TO);
        }
        return repository.byDate(currentAgencyProvider.currentAgencyId(), from, to);
    }
}
