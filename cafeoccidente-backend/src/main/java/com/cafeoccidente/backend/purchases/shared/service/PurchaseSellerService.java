package com.cafeoccidente.backend.purchases.shared.service;

import com.cafeoccidente.backend.purchases.shared.dto.PurchaseSeller;

/**
 * Datos del vendedor para el calculo y la factura, siempre desde TERCEROS (Q8). Lo digitado solo
 * se usa para la direccion o el celular que el tercero tiene vacios.
 */
public interface PurchaseSellerService {

    /** Para el preview: no escribe nada. */
    PurchaseSeller resolve(String idNumber, String typedAddress, String typedCellphone);

    /**
     * Para el guardado: ademas completa en TERCEROS la direccion o el celular que estaban vacios, con
     * su fila de historial. Nunca sobrescribe un dato que ya existe.
     */
    PurchaseSeller resolveAndComplete(String idNumber, String typedAddress, String typedCellphone);
}
