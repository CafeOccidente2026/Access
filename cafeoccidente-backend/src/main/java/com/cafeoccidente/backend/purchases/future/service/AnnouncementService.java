package com.cafeoccidente.backend.purchases.future.service;

import com.cafeoccidente.backend.purchases.future.dto.AnnouncementRequest;
import com.cafeoccidente.backend.purchases.future.dto.AnnouncementResponse;
import java.util.List;

/** Resuelve el anuncio de precio vigente para una agencia/fondo/especial y administra su alta. */
public interface AnnouncementService {
    AnnouncementResponse findLatest(Long agencyId, Long fundId, String specialType);

    /** Crea un anuncio nuevo para la agencia del admin autenticado (nunca edita uno existente). */
    AnnouncementResponse create(AnnouncementRequest request);

    /** Historial de anuncios de la agencia del admin autenticado, mas reciente primero. */
    List<AnnouncementResponse> history();
}
