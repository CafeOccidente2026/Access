package com.cafeoccidente.backend.purchases.shared.service;

import com.cafeoccidente.backend.controlrecord.entity.ControlRecord;
import com.cafeoccidente.backend.purchases.shared.dto.NextInvoiceNumberResponse;

/**
 * Consecutivo de factura del punto (HU-15, Q7): seco, verde y pasilla comparten una sola resolucion
 * DIAN por agencia (ControlRecord.resolutionFrom/resolutionTo), asi que comparten el numero. Lo
 * asigna el servidor al guardar; el que mande el cliente no se usa.
 */
public interface InvoiceConsecutiveService {

    /** Proximo numero para mostrarlo en el encabezado, con el aviso de resolucion vencida o por agotarse. */
    NextInvoiceNumberResponse next(Long agencyId, ControlRecord controlRecord);

    /** Toma el numero para la compra que se esta guardando; debe llamarse dentro de su transaccion. */
    int take(Long agencyId, ControlRecord controlRecord);
}
