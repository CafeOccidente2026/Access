package com.cafeoccidente.backend.purchases.greencoffee.service;

import com.cafeoccidente.backend.purchases.greencoffee.dto.AnnouncementInfoResponse;
import com.cafeoccidente.backend.purchases.greencoffee.dto.GreenCoffeePurchaseRequest;
import com.cafeoccidente.backend.purchases.greencoffee.dto.GreenCoffeePurchaseResponse;
import com.cafeoccidente.backend.purchases.greencoffee.dto.NextInvoiceNumberResponse;

public interface GreenCoffeePurchaseService {
    GreenCoffeePurchaseResponse create(GreenCoffeePurchaseRequest request);

    GreenCoffeePurchaseResponse findById(Long id);

    /** Recalcula la cascada completa sin persistir. */
    GreenCoffeePurchaseCalculation preview(GreenCoffeePurchaseRequest request);

    /** Factura: siguiente consecutivo propio de VERDES. */
    NextInvoiceNumberResponse nextInvoiceNumber();

    /** Cod Prod + anuncio vigente para la agencia del usuario (Fondo "RP" / Especial "CV" fijos). */
    AnnouncementInfoResponse announcementInfo();
}
