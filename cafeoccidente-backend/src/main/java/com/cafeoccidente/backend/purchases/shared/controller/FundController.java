package com.cafeoccidente.backend.purchases.shared.controller;

import com.cafeoccidente.backend.purchases.shared.dto.FundResponse;
import com.cafeoccidente.backend.purchases.shared.repository.FundRepository;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Solo lectura: la administracion de fondos no esta expuesta todavia (no fue solicitada). */
@RestController
@RequestMapping("/api/funds")
public class FundController {

    private final FundRepository fundRepository;

    public FundController(FundRepository fundRepository) {
        this.fundRepository = fundRepository;
    }

    @GetMapping
    public List<FundResponse> list() {
        return fundRepository.findByActiveTrue().stream()
                .map(fund -> new FundResponse(fund.getId(), fund.getCode(), fund.getName()))
                .toList();
    }
}
