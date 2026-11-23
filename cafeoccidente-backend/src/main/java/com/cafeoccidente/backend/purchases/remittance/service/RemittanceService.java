package com.cafeoccidente.backend.purchases.remittance.service;

import com.cafeoccidente.backend.purchases.remittance.dto.RemittanceFiles;
import com.cafeoccidente.backend.purchases.remittance.dto.RemittanceResponse;
import java.util.List;

/** Remesas (HU-13 / RF-11, HU-14 / RF-12) sobre la agencia de trabajo de la sesion (RN-01.3); las genera la remision. */
public interface RemittanceService {




    List<RemittanceResponse> findByNumber(String number);

    RemittanceFiles files(Long id);

    /** "Desmarca Remesa" de Access: solo el Administrador y solo remesas sin remision. */
    RemittanceResponse unmark(Long id);
}
