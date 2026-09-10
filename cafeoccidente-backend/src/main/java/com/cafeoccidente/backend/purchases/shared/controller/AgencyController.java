package com.cafeoccidente.backend.purchases.shared.controller;

import com.cafeoccidente.backend.purchases.shared.dto.AgencyResponse;
import com.cafeoccidente.backend.purchases.shared.repository.AgencyRepository;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Solo lectura: la administracion de agencias no esta expuesta todavia (no fue solicitada). */
@RestController
@RequestMapping("/api/agencies")
public class AgencyController {

    private final AgencyRepository agencyRepository;

    public AgencyController(AgencyRepository agencyRepository) {
        this.agencyRepository = agencyRepository;
    }

    @GetMapping
    public List<AgencyResponse> list() {
        return agencyRepository.findByActiveTrue().stream()
                .map(agency -> new AgencyResponse(agency.getId(), agency.getName()))
                .toList();
    }
}
