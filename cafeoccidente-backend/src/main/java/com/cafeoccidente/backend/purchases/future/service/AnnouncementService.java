package com.cafeoccidente.backend.purchases.future.service;

import com.cafeoccidente.backend.purchases.future.dto.AnnouncementResponse;

/** Unica responsabilidad: resolver el anuncio de precio vigente para una agencia/fondo. */
public interface AnnouncementService {
    AnnouncementResponse findLatest(Long agencyId, Long fundId);
}
