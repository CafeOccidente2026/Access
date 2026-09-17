package com.cafeoccidente.backend.purchases.future.service;

import com.cafeoccidente.backend.purchases.future.dto.AnnouncementRequest;
import com.cafeoccidente.backend.purchases.future.dto.AnnouncementResponse;
import java.util.List;

/** Resuelve el anuncio de precio vigente para una agencia/fondo/especial y administra su alta. */
public interface AnnouncementService {
    /** Costos/Pr Sustentacion/Bonificacion calculados en vivo con el ControlRecord de esa agencia. */
    AnnouncementResponse findLatest(Long agencyId, Long fundId, String specialType);

    /** Crea un anuncio maestro y lo numera para todas las agencias con ControlRecord activo (fan-out). */
    AnnouncementResponse create(AnnouncementRequest request);

    /** Historial de anuncios de la agencia del admin autenticado, mas reciente primero. */
    List<AnnouncementResponse> history();
}
