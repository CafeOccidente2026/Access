package com.cafeoccidente.backend.vendors.dto;

import java.util.List;

/**
 * Vista previa o resultado de Actualiza Cupos NESS (HU-54). Con errores no se carga nada y los conteos van en 0.
 * {@code rows}: cupos que quedan en la tabla (las repetidas con el mismo cupo cuentan una vez).
 */
public record NessQuotaUploadResult(
        List<String> errors, List<String> warnings, int rows, int added, int changed, int unchanged, int removed) {

    public static NessQuotaUploadResult rejected(NessQuotaFile file) {
        return new NessQuotaUploadResult(file.errors(), file.warnings(), 0, 0, 0, 0, 0);
    }
}
