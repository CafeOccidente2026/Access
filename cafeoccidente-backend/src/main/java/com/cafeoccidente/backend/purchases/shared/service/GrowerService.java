package com.cafeoccidente.backend.purchases.shared.service;

import com.cafeoccidente.backend.purchases.shared.dto.GrowerResponse;

public interface GrowerService {

    /** @throws com.cafeoccidente.backend.common.exception.ResourceNotFoundException si no existe (captura manual en el formulario). */
    GrowerResponse findByIdNumber(String idNumber);
}
