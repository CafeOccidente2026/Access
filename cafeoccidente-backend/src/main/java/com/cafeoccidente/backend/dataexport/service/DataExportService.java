package com.cafeoccidente.backend.dataexport.service;

import java.time.LocalDate;

/**
 * "Exportar Informacion" (macro ExportarCompras) y "Exportado Especial" (ExportadoEspecialBuys) del
 * Menu Principal, solo ADMIN. Devuelven un .zip con los 7 .xls que Access dejaba en
 * D:\AplicComprasArchivos y dejan marcado como exportado lo que salio. agencyId null = todas.
 */
public interface DataExportService {

    byte[] export(Long agencyId);

    /** Antes de exportar desmarca lo del rango de fechas (ambos extremos incluidos). */
    byte[] exportSpecial(Long agencyId, LocalDate from, LocalDate to);
}
