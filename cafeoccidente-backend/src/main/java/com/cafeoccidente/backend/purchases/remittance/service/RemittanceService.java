package com.cafeoccidente.backend.purchases.remittance.service;

import com.cafeoccidente.backend.purchases.remittance.dto.RemittanceFiles;
import com.cafeoccidente.backend.purchases.remittance.dto.RemittancePurchase;
import com.cafeoccidente.backend.purchases.remittance.dto.RemittanceRequest;
import com.cafeoccidente.backend.purchases.remittance.dto.RemittanceResponse;
import java.util.List;

/** Genera Remesa (HU-13 / RF-11, HU-14 / RF-12) sobre la agencia de trabajo de la sesion (RN-01.3). */
public interface RemittanceService {

    List<String> specials(String fundCode);

    List<RemittancePurchase> pending(String fundCode, String specialType);

    RemittanceResponse create(RemittanceRequest request);

    List<RemittanceResponse> findByNumber(String number);

    RemittanceFiles files(Long id);

    /** "Desmarca Remesa" de Access: solo el Administrador. */
    RemittanceResponse unmark(Long id);
}
