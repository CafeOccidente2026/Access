package com.cafeoccidente.backend.controlrecord.service.impl;

import com.cafeoccidente.backend.common.exception.ResourceNotFoundException;
import com.cafeoccidente.backend.controlrecord.entity.ControlRecord;
import com.cafeoccidente.backend.controlrecord.repository.ControlRecordRepository;
import com.cafeoccidente.backend.controlrecord.service.ControlRecordService;
import org.springframework.stereotype.Service;

@Service
public class ControlRecordServiceImpl implements ControlRecordService {

    private final ControlRecordRepository controlRecordRepository;

    public ControlRecordServiceImpl(ControlRecordRepository controlRecordRepository) {
        this.controlRecordRepository = controlRecordRepository;
    }

    @Override
    public ControlRecord getActive(Long agencyId) {
        return controlRecordRepository.findByAgencyIdAndActiveTrue(agencyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Esta agencia no tiene parametros de compra (ControlRecord) configurados todavia"));
    }
}
