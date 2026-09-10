package com.cafeoccidente.backend.purchases.shared.service;

import com.cafeoccidente.backend.purchases.shared.dto.MunicipalityRequest;
import com.cafeoccidente.backend.purchases.shared.dto.MunicipalityResponse;
import java.util.List;

public interface MunicipalityService {

    List<MunicipalityResponse> listActive();

    MunicipalityResponse create(MunicipalityRequest request);
}
