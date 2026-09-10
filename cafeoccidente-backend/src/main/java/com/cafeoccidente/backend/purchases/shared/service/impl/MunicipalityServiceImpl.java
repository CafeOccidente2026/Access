package com.cafeoccidente.backend.purchases.shared.service.impl;

import com.cafeoccidente.backend.purchases.shared.dto.MunicipalityRequest;
import com.cafeoccidente.backend.purchases.shared.dto.MunicipalityResponse;
import com.cafeoccidente.backend.purchases.shared.entity.Municipality;
import com.cafeoccidente.backend.purchases.shared.repository.MunicipalityRepository;
import com.cafeoccidente.backend.purchases.shared.service.MunicipalityService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class MunicipalityServiceImpl implements MunicipalityService {

    private final MunicipalityRepository municipalityRepository;

    public MunicipalityServiceImpl(MunicipalityRepository municipalityRepository) {
        this.municipalityRepository = municipalityRepository;
    }

    @Override
    public List<MunicipalityResponse> listActive() {
        return municipalityRepository.findByActiveTrue().stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public MunicipalityResponse create(MunicipalityRequest request) {
        Municipality municipality = new Municipality();
        municipality.setName(request.name());
        municipality.setActive(true);
        return toResponse(municipalityRepository.save(municipality));
    }

    private MunicipalityResponse toResponse(Municipality municipality) {
        return new MunicipalityResponse(municipality.getId(), municipality.getName(), municipality.isActive());
    }
}
