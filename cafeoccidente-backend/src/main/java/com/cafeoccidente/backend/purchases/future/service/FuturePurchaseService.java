package com.cafeoccidente.backend.purchases.future.service;

import com.cafeoccidente.backend.purchases.future.dto.AnnouncementResponse;
import com.cafeoccidente.backend.purchases.future.dto.FuturePurchaseRequest;
import com.cafeoccidente.backend.purchases.future.dto.FuturePurchaseResponse;
import java.util.List;

public interface FuturePurchaseService {

    /** RN-21.4.3 y RN-21.4.4: los anuncios de hoy (Colombia) del especial, fondo RP, de la agencia de trabajo. */
    List<AnnouncementResponse> todayAnnouncements(String specialType);

    FuturePurchaseResponse create(FuturePurchaseRequest request);

    FuturePurchaseResponse findById(Long id);
}
