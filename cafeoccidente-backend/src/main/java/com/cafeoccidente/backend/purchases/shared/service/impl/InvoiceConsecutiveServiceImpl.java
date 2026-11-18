package com.cafeoccidente.backend.purchases.shared.service.impl;

import com.cafeoccidente.backend.controlrecord.entity.ControlRecord;
import com.cafeoccidente.backend.controlrecord.service.DianResolutionRule;
import com.cafeoccidente.backend.purchases.shared.dto.NextInvoiceNumberResponse;
import com.cafeoccidente.backend.purchases.shared.repository.InvoiceConsecutiveRepository;
import com.cafeoccidente.backend.purchases.shared.service.InvoiceConsecutiveService;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvoiceConsecutiveServiceImpl implements InvoiceConsecutiveService {

    private final InvoiceConsecutiveRepository repository;
    private final Clock clock;

    public InvoiceConsecutiveServiceImpl(InvoiceConsecutiveRepository repository, Clock clock) {
        this.clock = clock;
        this.repository = repository;
    }

    /** Al empezar la compra: si la resolucion no sirve, el cajero lo sabe antes de digitar. */
    @Override
    public NextInvoiceNumberResponse next(Long agencyId, ControlRecord controlRecord) {
        int next = usable(repository.peek(agencyId, controlRecord.getResolutionFrom()), controlRecord);
        // ponytail: umbral fijo de la version anterior; pendiente de que el area diga cuantos numeros (R1).
        String warning = controlRecord.getResolutionTo() - next < 100
                ? "La resolución de facturación está a punto de agotarse"
                : null;
        return new NextInvoiceNumberResponse(next, controlRecord.getPrefix(), warning);
    }

    /** MANDATORY: fuera de la transaccion del guardado el numero se consumiria aunque la compra fallara. */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public int take(Long agencyId, ControlRecord controlRecord) {
        // Si la resolucion no sirve, la excepcion hace rollback y el consecutivo no sube.
        return usable(repository.take(agencyId, controlRecord.getResolutionFrom()), controlRecord);
    }

    private int usable(int number, ControlRecord controlRecord) {
        DianResolutionRule.requireUsable(controlRecord, number, LocalDate.now(clock));
        return number;
    }
}
