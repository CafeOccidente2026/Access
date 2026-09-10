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
    public ControlRecord getActive() {
        return controlRecordRepository.findByActiveTrue()
                .orElseThrow(() -> new ResourceNotFoundException("No hay un registro de control activo (ver Flyway V4)"));
    }
}
