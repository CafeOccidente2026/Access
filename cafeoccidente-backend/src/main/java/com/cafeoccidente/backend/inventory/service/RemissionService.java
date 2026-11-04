package com.cafeoccidente.backend.inventory.service;

import com.cafeoccidente.backend.inventory.dto.RemissionRequest;
import com.cafeoccidente.backend.inventory.dto.RemissionResponse;
import java.util.List;
import java.util.Optional;

public interface RemissionService {

    RemissionResponse create(RemissionRequest request);

    RemissionResponse findById(Long id);

    List<RemissionResponse> listByAgency(Long agencyId);

    /** "Reimprimir Remision": busca por numero correlativo dentro de la agencia. */
    /** Por numero impreso: el nuevo (SDTA-RP-0001) o el de las remisiones viejas ("1", "2"...). */
    Optional<RemissionResponse> findByNumber(Long agencyId, String displayNumber);
}
