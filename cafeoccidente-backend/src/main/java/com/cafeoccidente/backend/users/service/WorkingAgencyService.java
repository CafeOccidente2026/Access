package com.cafeoccidente.backend.users.service;

import com.cafeoccidente.backend.users.dto.MeResponse;

/** Sesion del usuario autenticado y su agencia de trabajo (RF-05.1). */
public interface WorkingAgencyService {

    MeResponse me();

    MeResponse choose(Long agencyId);
}
