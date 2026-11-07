package com.cafeoccidente.backend.purchases.report.service.impl;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.common.security.CurrentAgencyProvider;
import com.cafeoccidente.backend.controlrecord.service.ControlRecordService;
import com.cafeoccidente.backend.purchases.report.dto.FutureBalanceRow;
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
    private final ControlRecordService controlRecordService;

    public PurchaseReportServiceImpl(PurchaseReportRepository repository, CurrentAgencyProvider currentAgencyProvider,
            ControlRecordService controlRecordService) {
        this.repository = repository;
        this.currentAgencyProvider = currentAgencyProvider;
        this.controlRecordService = controlRecordService;
    }

    @Override
    public List<PurchaseReportRow> byDate(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new BusinessRuleException(FROM_AFTER_TO);
        }
        return repository.byDate(currentAgencyProvider.currentAgencyId(), from, to);
    }

    @Override
    public List<PurchaseReportRow> futureAnnouncement(int announcementNumber, LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new BusinessRuleException(FROM_AFTER_TO);
        }
        Long agencyId = currentAgencyProvider.currentAgencyId();
        return repository.futureAnnouncement(agencyId, displayNumber(agencyId, announcementNumber), from, to);
    }

    @Override
    public List<FutureBalanceRow> futureBalance(int announcementNumber) {
        Long agencyId = currentAgencyProvider.currentAgencyId();
        return repository.futureBalance(agencyId, displayNumber(agencyId, announcementNumber));
    }

    /** Las compras guardan el anuncio como "PREFIJO-numero"; el usuario digita solo el numero, como en Access. */
    private String displayNumber(Long agencyId, int announcementNumber) {
        return controlRecordService.getActive(agencyId).getPrefix() + "-" + announcementNumber;
    }
}
