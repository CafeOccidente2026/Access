package com.cafeoccidente.backend.purchases.future.service;

import com.cafeoccidente.backend.purchases.future.dto.AnnouncementQuotaRequest;
import com.cafeoccidente.backend.purchases.future.dto.AnnouncementQuotaResponse;
import com.cafeoccidente.backend.purchases.future.dto.QuotaCandidateResponse;
import java.util.List;

/** Pantalla "Asignar Cupo" (Access "ACTUALIZA CUPOS"): cupo total por anuncio, no por caficultor. */
public interface AnnouncementQuotaService {

    List<AnnouncementQuotaResponse> list(Long agencyId);

    /** RN-21.1.1 a RN-21.1.6: anuncios de la agencia sin cupo, sin pasilla ni verde, con sus Kilos Entregados. */
    List<QuotaCandidateResponse> available(Long agencyId);

    AnnouncementQuotaResponse get(Long agencyId, Integer announcementNumber);

    /** Crea el cupo del anuncio una sola vez (RN-21.1.9); rechaza un cupo menor que lo ya entregado. */
    AnnouncementQuotaResponse assign(Long agencyId, Integer announcementNumber, AnnouncementQuotaRequest request);
}
