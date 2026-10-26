package com.cafeoccidente.backend.purchases.future.service;

import com.cafeoccidente.backend.purchases.future.dto.AnnouncementRequest;
import com.cafeoccidente.backend.purchases.future.dto.AnnouncementResponse;
import com.cafeoccidente.backend.purchases.future.dto.AnnouncementTypeRequest;
import com.cafeoccidente.backend.purchases.future.dto.AnnouncementTypeResponse;
import com.cafeoccidente.backend.purchases.future.dto.HuskAnnouncementRequest;
import java.util.List;

/** Resuelve el anuncio de precio vigente para una agencia/fondo/especial y administra su alta. */
public interface AnnouncementService {
    /** Costos/Pr Sustentacion/Bonificacion calculados en vivo con el ControlRecord de esa agencia. */
    AnnouncementResponse findLatest(Long agencyId, Long fundId, String specialType);

    /**
     * "Actualizar Anuncio con Factor" (RF-08): un anuncio maestro por cada tipo de cafe del fondo, cada uno
     * numerado para todas las agencias con ControlRecord activo (fan-out).
     */
    List<AnnouncementResponse> publishAll(AnnouncementRequest request);

    /** Listado "Actualizar" (RN-08.5): los tipos de cafe del fondo con su anuncio vigente. */
    List<AnnouncementTypeResponse> types(Long fundId);

    /** Listado "Actualizar" (RN-08.6): un anuncio nuevo solo para el tipo elegido. */
    AnnouncementResponse publishType(AnnouncementTypeRequest request);

    /** "Actualizar Anuncio Pasilla": mismo fan-out que create(), Fondo RP y Especial PASILLA fijos. */
    AnnouncementResponse createHusk(HuskAnnouncementRequest request);

    /** Historial de anuncios de la agencia del admin autenticado, mas reciente primero. */
    List<AnnouncementResponse> history();
}
