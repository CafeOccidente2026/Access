package com.cafeoccidente.backend.purchases.future.service;

import com.cafeoccidente.backend.purchases.future.dto.AnnouncementQuotaRequest;
import com.cafeoccidente.backend.purchases.future.dto.AnnouncementQuotaResponse;
import java.util.List;

/** Pantalla "Asignar Cupo" (Access "ACTUALIZA CUPOS"): cupo total por anuncio, no por caficultor. */
public interface AnnouncementQuotaService {

    List<AnnouncementQuotaResponse> list(Long agencyId);

    AnnouncementQuotaResponse get(Long agencyId, Integer announcementNumber);

    /** Crea o reemplaza el cupo asignado a ese anuncio (upsert, como "Actualizar Cupo" en Access). */
    AnnouncementQuotaResponse assign(Long agencyId, Integer announcementNumber, AnnouncementQuotaRequest request);
}
