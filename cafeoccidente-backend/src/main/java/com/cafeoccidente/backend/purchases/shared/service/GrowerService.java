package com.cafeoccidente.backend.purchases.shared.service;

import com.cafeoccidente.backend.purchases.shared.dto.GrowerProgramResponse;
import com.cafeoccidente.backend.purchases.shared.dto.GrowerResponse;
import java.math.BigDecimal;
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

    /**
     * Item C: bloquea si esta compra excede el cupo asignado al caficultor para este Especial
     * (Form_COMPRAS.bas: {@code If Kilos_Netos.Value > Texto118.Value Then "CAFICULTOR EXCEDE CUPO
     * ASIGNADO..."}). Usa la misma fuente que {@link #findProgram}
     * (staging_legacy_ness/SPECIAL_TO_PROGRAMA) - mismo alcance parcial (3 de ~20 Especiales); si no
     * hay match, no bloquea (dato no migrado, no evidencia de que no haya cupo).
     *
     * @throws com.cafeoccidente.backend.common.exception.BusinessRuleException si excede el cupo.
     */
    void checkQuota(String idNumber, String specialType, BigDecimal netKg);
}
