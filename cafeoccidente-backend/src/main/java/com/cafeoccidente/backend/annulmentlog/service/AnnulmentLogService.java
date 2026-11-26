package com.cafeoccidente.backend.annulmentlog.service;

import com.cafeoccidente.backend.annulmentlog.dto.AnnulmentLogFilter;
import com.cafeoccidente.backend.annulmentlog.dto.AnnulmentLogPage;
import com.cafeoccidente.backend.purchases.annulment.repository.AnnulmentModule;

/** Registro de anulaciones para auditoria (26-nov): solo se inserta y se consulta. */
public interface AnnulmentLogService {

    /** Anota la anulacion de una compra con la foto de sus valores; se llama antes de dejarla en 0. */
    void recordPurchase(AnnulmentModule module, Long purchaseId, String reason);

    /** Anota la anulacion de una remision con la foto de sus valores; se llama antes de devolver los saldos. */
    void recordRemission(Long remissionId, String reason);

    AnnulmentLogPage search(AnnulmentLogFilter filter);
}
