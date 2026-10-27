package com.cafeoccidente.backend.purchases.shared.service.impl;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.controlrecord.entity.ControlRecord;
import com.cafeoccidente.backend.purchases.shared.dto.NextInvoiceNumberResponse;
import com.cafeoccidente.backend.purchases.shared.repository.InvoiceConsecutiveRepository;
import com.cafeoccidente.backend.purchases.shared.service.InvoiceConsecutiveService;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvoiceConsecutiveServiceImpl implements InvoiceConsecutiveService {

    static final String RANGE_EXHAUSTED = "Se agotó el rango de facturas autorizado por la resolución DIAN vigente";

    private final InvoiceConsecutiveRepository repository;

    public InvoiceConsecutiveServiceImpl(InvoiceConsecutiveRepository repository) {
        this.repository = repository;
    }

    @Override
    public NextInvoiceNumberResponse next(Long agencyId, ControlRecord controlRecord) {
        int next = withinRange(repository.peek(agencyId, controlRecord.getResolutionFrom()), controlRecord);
        String warning = null;
        LocalDate expiration = controlRecord.getResolutionDate().plusMonths(controlRecord.getValidity());
        if (LocalDate.now().isAfter(expiration)) {
            warning = "La resolución de facturación está vencida";
        } else if (controlRecord.getResolutionTo() - next < 100) {
            warning = "La resolución de facturación está a punto de agotarse";
        }
        return new NextInvoiceNumberResponse(next, controlRecord.getPrefix(), warning);
    }

    /** MANDATORY: fuera de la transaccion del guardado el numero se consumiria aunque la compra fallara. */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public int take(Long agencyId, ControlRecord controlRecord) {
        // Si se pasa del rango, la excepcion hace rollback y el consecutivo no sube.
        return withinRange(repository.take(agencyId, controlRecord.getResolutionFrom()), controlRecord);
    }

    private static int withinRange(int number, ControlRecord controlRecord) {
        if (number > controlRecord.getResolutionTo()) {
            throw new BusinessRuleException(RANGE_EXHAUSTED);
        }
        return number;
    }
}
