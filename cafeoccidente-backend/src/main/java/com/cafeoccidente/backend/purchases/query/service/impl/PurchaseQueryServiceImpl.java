package com.cafeoccidente.backend.purchases.query.service.impl;

import com.cafeoccidente.backend.common.exception.ResourceNotFoundException;
import com.cafeoccidente.backend.common.security.CurrentAgencyProvider;
import com.cafeoccidente.backend.purchases.annulment.dto.AnnulmentCandidate;
import com.cafeoccidente.backend.purchases.annulment.repository.AnnulmentDirection;
import com.cafeoccidente.backend.purchases.annulment.repository.AnnulmentModule;
import com.cafeoccidente.backend.purchases.annulment.service.PurchaseAnnulmentService;
import com.cafeoccidente.backend.purchases.drycoffee.service.DryCoffeePurchaseService;
import com.cafeoccidente.backend.purchases.greencoffee.service.GreenCoffeePurchaseService;
import com.cafeoccidente.backend.purchases.husk.service.HuskPurchaseService;
import com.cafeoccidente.backend.purchases.query.dto.PurchaseQueryDetail;
import com.cafeoccidente.backend.purchases.query.repository.PurchaseQueryRepository;
import com.cafeoccidente.backend.purchases.query.service.PurchaseQueryService;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Buscar y recorrer usan las mismas lecturas de "Anular Documento" (misma ficha Cns_Compras, mismo SQL);
 * aca solo cambia quien entra (Administrador y Usuario).
 */
@Service
public class PurchaseQueryServiceImpl implements PurchaseQueryService {

    private final PurchaseAnnulmentService annulment;
    private final PurchaseQueryRepository repository;
    private final DryCoffeePurchaseService dry;
    private final GreenCoffeePurchaseService green;
    private final HuskPurchaseService husk;
    private final CurrentAgencyProvider currentAgencyProvider;

    public PurchaseQueryServiceImpl(PurchaseAnnulmentService annulment, PurchaseQueryRepository repository,
            DryCoffeePurchaseService dry, GreenCoffeePurchaseService green, HuskPurchaseService husk,
            CurrentAgencyProvider currentAgencyProvider) {
        this.annulment = annulment;
        this.repository = repository;
        this.dry = dry;
        this.green = green;
        this.husk = husk;
        this.currentAgencyProvider = currentAgencyProvider;
    }

    @Override
    public List<AnnulmentCandidate> findByInvoice(Integer invoiceNumber) {
        return annulment.findByInvoice(agency(), invoiceNumber);
    }

    @Override
    public List<AnnulmentCandidate> navigate(Integer from, AnnulmentDirection direction) {
        return annulment.navigate(agency(), from, direction);
    }

    @Override
    public PurchaseQueryDetail detail(AnnulmentModule module, Long id) {
        PurchaseQueryRepository.Header header = repository.header(module, id)
                .filter(h -> h.agencyId().equals(agency()))
                .orElseThrow(() -> new ResourceNotFoundException("No existe esa compra en la agencia"));
        Object purchase = switch (module) {
            case DRY -> dry.findById(id);
            case GREEN -> green.findById(id);
            case HUSK -> husk.findById(id);
        };
        return new PurchaseQueryDetail(module, purchase, header.status(), repository.payment(module, id).orElse(null),
                repository.remissions(module, id));
    }

    private Long agency() {
        return currentAgencyProvider.currentAgencyId();
    }
}
