package com.cafeoccidente.backend.purchases.future.service;

import com.cafeoccidente.backend.purchases.future.dto.AnnouncementQuotaRequest;
import com.cafeoccidente.backend.purchases.future.dto.AnnouncementQuotaResponse;
import com.cafeoccidente.backend.purchases.future.dto.QuotaBalanceResponse;
import com.cafeoccidente.backend.purchases.future.dto.QuotaCandidateResponse;
import java.math.BigDecimal;
import java.util.List;

/** Pantalla "Asignar Cupo" (Access "ACTUALIZA CUPOS"): cupo total por anuncio, no por caficultor. */
public interface AnnouncementQuotaService {

    List<AnnouncementQuotaResponse> list(Long agencyId);

    /** RN-21.1.1 a RN-21.1.6: anuncios de la agencia sin cupo, sin pasilla ni verde, con sus Kilos Entregados. */
    List<QuotaCandidateResponse> available(Long agencyId);

    /** RN-21.2.3 y RN-21.2.4: anuncios del fondo y especial con cupo y saldo mayor que cero (la ventana). */
    List<QuotaBalanceResponse> withBalance(Long agencyId, Long fundId, String specialType);

    /**
     * Dentro de la transaccion de una compra de seco: si su anuncio tiene cupo, bloquea la fila del cupo, recalcula
     * el saldo y rechaza si los kilos netos lo superan (HU-29 CA-4, RN-21.2.5). Con {@code deliveryKg} (Esta Entrega)
     * el anuncio tiene que tener cupo, la entrega no puede pasar el saldo y los kilos netos no pueden pasar la entrega.
     */
    void reserve(Long agencyId, String displayNumber, BigDecimal netKg, BigDecimal deliveryKg);

    AnnouncementQuotaResponse get(Long agencyId, Integer announcementNumber);

    /** Crea el cupo del anuncio una sola vez (RN-21.1.9); rechaza un cupo menor que lo ya entregado. */
    AnnouncementQuotaResponse assign(Long agencyId, Integer announcementNumber, AnnouncementQuotaRequest request);
}
