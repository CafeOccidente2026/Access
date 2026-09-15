package com.cafeoccidente.backend.purchases.shared.service;

import com.cafeoccidente.backend.purchases.shared.dto.GrowerProgramResponse;
import com.cafeoccidente.backend.purchases.shared.dto.GrowerResponse;
import java.util.Optional;

public interface GrowerService {

    /** @throws com.cafeoccidente.backend.common.exception.ResourceNotFoundException si no existe (captura manual en el formulario). */
    GrowerResponse findByIdNumber(String idNumber);

    /**
     * Programa/Cupo informativos desde staging_legacy_ness (Compras Cafe Seco). Vacio si no hay
     * match o si es ambiguo sin Especial (caficultor en mas de un programa) - nunca bloquea.
     *
     * @param specialType Especial seleccionado en el formulario, o null si aun no se ha elegido.
     */
    Optional<GrowerProgramResponse> findProgram(String idNumber, String specialType);
}
