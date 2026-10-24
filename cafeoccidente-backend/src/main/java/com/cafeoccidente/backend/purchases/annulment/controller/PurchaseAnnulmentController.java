package com.cafeoccidente.backend.purchases.annulment.controller;

import com.cafeoccidente.backend.common.security.CurrentAgencyProvider;
import com.cafeoccidente.backend.common.security.Roles;
import com.cafeoccidente.backend.purchases.annulment.dto.AnnulmentCandidate;
import com.cafeoccidente.backend.purchases.annulment.repository.AnnulmentModule;
import com.cafeoccidente.backend.purchases.annulment.service.PurchaseAnnulmentService;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** "Anular Documento" del menu Compras: USER solo su agencia, ADMIN cualquiera (decision P6). */
@RestController
@RequestMapping("/api/purchases/annulment")
@PreAuthorize(Roles.ANNUL)
public class PurchaseAnnulmentController {

    private final PurchaseAnnulmentService service;
    private final CurrentAgencyProvider currentAgencyProvider;

    public PurchaseAnnulmentController(PurchaseAnnulmentService service, CurrentAgencyProvider currentAgencyProvider) {
        this.service = service;
        this.currentAgencyProvider = currentAgencyProvider;
    }

    @GetMapping
    public List<AnnulmentCandidate> findByInvoice(@RequestParam Integer invoiceNumber) {
        return service.findByInvoice(agency(), invoiceNumber);
    }

    @PostMapping("/{module}/{id}")
    public AnnulmentCandidate annul(@PathVariable AnnulmentModule module, @PathVariable Long id) {
        return service.annul(agency(), module, id);
    }

    /** Anular Documento trabaja solo sobre la agencia de trabajo (RF-05.1). */
    private Long agency() {
        return currentAgencyProvider.currentAgencyId();
    }
}
