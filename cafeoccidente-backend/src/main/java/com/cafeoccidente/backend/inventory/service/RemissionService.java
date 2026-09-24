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
    Optional<RemissionResponse> findByNumber(Long agencyId, Integer remissionNumber);

    /** Remisiones aun no incluidas en una remesa (Access "Genera Remesa"/"Genera Remesa Otros"). */
    List<RemissionResponse> listPendingExport(Long agencyId);

    /** Marca como exportadas (Access TransferSpreadsheet a D:\ + envio a Almacafe/SAP, reemplazado
     *  por descarga de Excel en el navegador - ver RemesaExportComponent). */
    List<RemissionResponse> markExported(List<Long> remissionIds);
}
