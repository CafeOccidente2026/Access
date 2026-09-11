package com.cafeoccidente.backend.purchases.shared.service.impl;

import com.cafeoccidente.backend.common.exception.ResourceNotFoundException;
import com.cafeoccidente.backend.purchases.shared.dto.GrowerResponse;
import com.cafeoccidente.backend.purchases.shared.entity.Grower;
import com.cafeoccidente.backend.purchases.shared.repository.GrowerRepository;
import com.cafeoccidente.backend.purchases.shared.service.GrowerService;
import org.springframework.stereotype.Service;

@Service
public class GrowerServiceImpl implements GrowerService {

    private final GrowerRepository growerRepository;

    public GrowerServiceImpl(GrowerRepository growerRepository) {
        this.growerRepository = growerRepository;
    }

    @Override
    public GrowerResponse findByIdNumber(String idNumber) {
        Grower grower = growerRepository.findByIdNumber(idNumber)
                .orElseThrow(() -> new ResourceNotFoundException("No existe un caficultor con esa cedula"));
        return new GrowerResponse(
                grower.getId(),
                grower.getIdNumber(),
                grower.getFirstName(),
                grower.getSecondName(),
                grower.getLastName(),
                grower.getSecondLastName(),
                grower.getAddress(),
                grower.getPhone(),
                grower.getGrowerType(),
                grower.isActive(),
                grower.isDeceased(),
                grower.isWithdrawn());
    }
}
