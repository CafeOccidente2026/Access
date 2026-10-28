package com.cafeoccidente.backend.purchases.shared.service;

import com.cafeoccidente.backend.purchases.shared.dto.PurchaseContext;

/** Arma el {@link PurchaseContext} comun a seco, verde y pasilla, siempre con la agencia del token. */
public interface PurchaseContextService {

    /** Para el preview: no escribe nada. */
    PurchaseContext forPreview(Long fundId, String specialType, String idNumber, String address, String cellphone);

    /** Para el guardado: ademas completa la direccion o el celular vacios del tercero (Q8). */
    PurchaseContext forSave(Long fundId, String specialType, String idNumber, String address, String cellphone);
}
