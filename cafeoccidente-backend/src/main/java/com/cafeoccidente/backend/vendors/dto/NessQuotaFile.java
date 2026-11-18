package com.cafeoccidente.backend.vendors.dto;

import java.util.List;

/**
 * Excel de Actualiza Cupos NESS ya leido (HU-54): los cupos listos para cargar, los errores (con uno solo no se
 * carga nada) y los avisos (repetidas con el mismo cupo, que se cargan una sola vez).
 */
public record NessQuotaFile(List<NessQuotaRow> lines, List<String> errors, List<String> warnings) {
}
