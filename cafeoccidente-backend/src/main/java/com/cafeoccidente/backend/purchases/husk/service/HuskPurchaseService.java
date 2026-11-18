package com.cafeoccidente.backend.purchases.husk.service;

import com.cafeoccidente.backend.purchases.husk.dto.AnnouncementInfoResponse;
import com.cafeoccidente.backend.purchases.husk.dto.HuskPurchaseRequest;
import com.cafeoccidente.backend.purchases.husk.dto.HuskPurchaseResponse;
import com.cafeoccidente.backend.purchases.shared.dto.NextInvoiceNumberResponse;

public interface HuskPurchaseService {
    HuskPurchaseResponse create(HuskPurchaseRequest request);

    /** HU-05: corrige la compra de la agencia de trabajo sin anularla y con el mismo numero de factura. */
    HuskPurchaseResponse correct(Long id, HuskPurchaseRequest request);

    /** Vista previa de la correccion: el calculo con el anuncio original, sin guardar. */
    HuskPurchaseCalculation previewCorrection(Long id, HuskPurchaseRequest request);

    HuskPurchaseResponse findById(Long id);

    HuskPurchaseCalculation preview(HuskPurchaseRequest request);

    /** Factura: siguiente consecutivo propio de PASILLA. */
    NextInvoiceNumberResponse nextInvoiceNumber();

    /** Cod Prod + anuncio vigente para la agencia del usuario (Fondo "RP" / Especial "PASILLA"). */
    AnnouncementInfoResponse announcementInfo();
}
