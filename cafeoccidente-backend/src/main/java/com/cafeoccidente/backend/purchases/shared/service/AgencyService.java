package com.cafeoccidente.backend.purchases.shared.service;

import com.cafeoccidente.backend.purchases.shared.dto.AgencyRequest;
import com.cafeoccidente.backend.purchases.shared.dto.AgencyResponse;
import java.util.List;

public interface AgencyService {

    /** Agencias activas, con su estado de Registro de Control y su prefijo sugerido. */
    List<AgencyResponse> list();

    /** HU-09 (RN-07.4): la agencia nueva queda activa y aparece de inmediato en las listas. */
    AgencyResponse create(AgencyRequest request);
}
