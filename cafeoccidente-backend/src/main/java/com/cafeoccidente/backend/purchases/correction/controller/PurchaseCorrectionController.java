package com.cafeoccidente.backend.purchases.correction.controller;

import com.cafeoccidente.backend.common.security.CurrentAgencyProvider;
import com.cafeoccidente.backend.common.security.Roles;
import com.cafeoccidente.backend.purchases.annulment.dto.AnnulmentCandidate;
import com.cafeoccidente.backend.purchases.annulment.repository.AnnulmentDirection;
import com.cafeoccidente.backend.purchases.annulment.service.PurchaseAnnulmentService;
import com.cafeoccidente.backend.purchases.drycoffee.dto.DryCoffeePurchaseRequest;
import com.cafeoccidente.backend.purchases.drycoffee.dto.DryCoffeePurchaseResponse;
import com.cafeoccidente.backend.purchases.drycoffee.service.DryCoffeePurchaseCalculation;
import com.cafeoccidente.backend.purchases.drycoffee.service.DryCoffeePurchaseService;
import com.cafeoccidente.backend.purchases.greencoffee.dto.GreenCoffeePurchaseRequest;
import com.cafeoccidente.backend.purchases.greencoffee.dto.GreenCoffeePurchaseResponse;
import com.cafeoccidente.backend.purchases.greencoffee.service.GreenCoffeePurchaseCalculation;
import com.cafeoccidente.backend.purchases.greencoffee.service.GreenCoffeePurchaseService;
import com.cafeoccidente.backend.purchases.husk.dto.HuskPurchaseRequest;
import com.cafeoccidente.backend.purchases.husk.dto.HuskPurchaseResponse;
import com.cafeoccidente.backend.purchases.husk.service.HuskPurchaseCalculation;
import com.cafeoccidente.backend.purchases.husk.service.HuskPurchaseService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * "Menu Habilitacion" (HU-05, respuesta del area 11-nov): corregir una compra sin anularla. El Administrador o
 * quien tenga el permiso Corregir Compra, en la agencia de trabajo. Busca y recorre las facturas como Anular
 * Documento; los datos completos de la compra salen de GET /api/purchases/{modulo}/{id}.
 */
@RestController
@RequestMapping("/api/purchases/correction")
@PreAuthorize(Roles.CORRECT)
public class PurchaseCorrectionController {

    private final PurchaseAnnulmentService annulmentService;
    private final DryCoffeePurchaseService dryCoffeePurchaseService;
    private final GreenCoffeePurchaseService greenCoffeePurchaseService;
    private final HuskPurchaseService huskPurchaseService;
    private final CurrentAgencyProvider currentAgencyProvider;

    public PurchaseCorrectionController(PurchaseAnnulmentService annulmentService,
            DryCoffeePurchaseService dryCoffeePurchaseService, GreenCoffeePurchaseService greenCoffeePurchaseService,
            HuskPurchaseService huskPurchaseService, CurrentAgencyProvider currentAgencyProvider) {
        this.annulmentService = annulmentService;
        this.dryCoffeePurchaseService = dryCoffeePurchaseService;
        this.greenCoffeePurchaseService = greenCoffeePurchaseService;
        this.huskPurchaseService = huskPurchaseService;
        this.currentAgencyProvider = currentAgencyProvider;
    }

    @GetMapping
    public List<AnnulmentCandidate> findByInvoice(@RequestParam Integer invoiceNumber) {
        return annulmentService.findByInvoice(currentAgencyProvider.currentAgencyId(), invoiceNumber);
    }

    @GetMapping("/navigate")
    public List<AnnulmentCandidate> navigate(@RequestParam(required = false) Integer from,
            @RequestParam AnnulmentDirection direction) {
        return annulmentService.navigate(currentAgencyProvider.currentAgencyId(), from, direction);
    }

    @PostMapping("/DRY/{id}/preview")
    public DryCoffeePurchaseCalculation previewDry(@PathVariable Long id,
            @Valid @RequestBody DryCoffeePurchaseRequest request) {
        return dryCoffeePurchaseService.previewCorrection(id, request);
    }

    @PostMapping("/GREEN/{id}/preview")
    public GreenCoffeePurchaseCalculation previewGreen(@PathVariable Long id,
            @Valid @RequestBody GreenCoffeePurchaseRequest request) {
        return greenCoffeePurchaseService.previewCorrection(id, request);
    }

    @PostMapping("/HUSK/{id}/preview")
    public HuskPurchaseCalculation previewHusk(@PathVariable Long id, @Valid @RequestBody HuskPurchaseRequest request) {
        return huskPurchaseService.previewCorrection(id, request);
    }

    @PutMapping("/DRY/{id}")
    public DryCoffeePurchaseResponse correctDry(@PathVariable Long id, @Valid @RequestBody DryCoffeePurchaseRequest request) {
        return dryCoffeePurchaseService.correct(id, request);
    }

    @PutMapping("/GREEN/{id}")
    public GreenCoffeePurchaseResponse correctGreen(@PathVariable Long id,
            @Valid @RequestBody GreenCoffeePurchaseRequest request) {
        return greenCoffeePurchaseService.correct(id, request);
    }

    @PutMapping("/HUSK/{id}")
    public HuskPurchaseResponse correctHusk(@PathVariable Long id, @Valid @RequestBody HuskPurchaseRequest request) {
        return huskPurchaseService.correct(id, request);
    }
}
