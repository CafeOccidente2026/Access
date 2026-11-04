package com.cafeoccidente.backend.purchases.query.controller;

import com.cafeoccidente.backend.common.security.Roles;
import com.cafeoccidente.backend.purchases.annulment.dto.AnnulmentCandidate;
import com.cafeoccidente.backend.purchases.annulment.repository.AnnulmentDirection;
import com.cafeoccidente.backend.purchases.annulment.repository.AnnulmentModule;
import com.cafeoccidente.backend.purchases.query.dto.PurchaseQueryDetail;
import com.cafeoccidente.backend.purchases.query.service.PurchaseQueryService;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** "Consultar Compras" del menu Compras (RF-26): Administrador y Usuario, solo lectura. */
@RestController
@RequestMapping("/api/purchases/query")
@PreAuthorize(Roles.ADMIN_USER)
public class PurchaseQueryController {

    private final PurchaseQueryService service;

    public PurchaseQueryController(PurchaseQueryService service) {
        this.service = service;
    }

    @GetMapping
    public List<AnnulmentCandidate> findByInvoice(@RequestParam Integer invoiceNumber) {
        return service.findByInvoice(invoiceNumber);
    }

    @GetMapping("/navigate")
    public List<AnnulmentCandidate> navigate(@RequestParam(required = false) Integer from,
            @RequestParam AnnulmentDirection direction) {
        return service.navigate(from, direction);
    }

    @GetMapping("/{module}/{id}")
    public PurchaseQueryDetail detail(@PathVariable AnnulmentModule module, @PathVariable Long id) {
        return service.detail(module, id);
    }
}
