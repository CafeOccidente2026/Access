package com.cafeoccidente.backend.vendors.service;

import com.cafeoccidente.backend.vendors.dto.NessQuotaUploadRecord;
import com.cafeoccidente.backend.vendors.dto.NessQuotaUploadResult;
import java.util.List;

/** Actualiza Cupos NESS (HU-54, RF-28.4): solo el Administrador. */
public interface NessQuotaUploadService {

    /** Valida el archivo y cuenta que cambiaria, sin tocar la tabla NESS. */
    NessQuotaUploadResult preview(String fileName, byte[] content);

    /** Valida de nuevo y reemplaza la tabla NESS en una transaccion; con errores no cambia nada. */
    NessQuotaUploadResult apply(String fileName, byte[] content);

    /** Cargas anteriores, la mas reciente primero. */
    List<NessQuotaUploadRecord> history();
}
