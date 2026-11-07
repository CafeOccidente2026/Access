package com.cafeoccidente.backend.purchases.shared.service;

import com.cafeoccidente.backend.purchases.future.dto.AnnouncementResponse;
import com.cafeoccidente.backend.purchases.shared.dto.PurchaseContext;

/** Arma el {@link PurchaseContext} comun a seco, verde y pasilla, siempre con la agencia del token. */
public interface PurchaseContextService {

    /**
     * Para el preview: no escribe nada. {@code announcement} es el que eligio el usuario (anuncio con cupo o compra
     * anunciada); null = el anuncio vigente.
     */
    PurchaseContext forPreview(Long fundId, String specialType, AnnouncementResponse announcement, String idNumber,
            String address, String cellphone);

    /** Para el guardado: ademas completa la direccion o el celular vacios del tercero (Q8). */
    PurchaseContext forSave(Long fundId, String specialType, AnnouncementResponse announcement, String idNumber,
            String address, String cellphone);

    /** Con el anuncio vigente (verde, pasilla y seco sin cupo). */
    default PurchaseContext forPreview(Long fundId, String specialType, String idNumber, String address, String cellphone) {
        return forPreview(fundId, specialType, (AnnouncementResponse) null, idNumber, address, cellphone);
    }

    /** Con el anuncio vigente (verde, pasilla y seco sin cupo). */
    default PurchaseContext forSave(Long fundId, String specialType, String idNumber, String address, String cellphone) {
        return forSave(fundId, specialType, (AnnouncementResponse) null, idNumber, address, cellphone);
    }
}
