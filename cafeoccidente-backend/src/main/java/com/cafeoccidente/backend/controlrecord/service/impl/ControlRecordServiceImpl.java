package com.cafeoccidente.backend.controlrecord.service.impl;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.common.exception.ResourceNotFoundException;
import com.cafeoccidente.backend.controlrecord.dto.ControlRecordRequest;
import com.cafeoccidente.backend.controlrecord.dto.ControlRecordResponse;
import com.cafeoccidente.backend.controlrecord.entity.ControlRecord;
import com.cafeoccidente.backend.controlrecord.mapper.ControlRecordMapper;
import com.cafeoccidente.backend.controlrecord.repository.ControlRecordRepository;
import com.cafeoccidente.backend.controlrecord.service.ControlRecordService;
import com.cafeoccidente.backend.purchases.shared.entity.Agency;
import com.cafeoccidente.backend.purchases.shared.repository.AgencyRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ControlRecordServiceImpl implements ControlRecordService {

    private final ControlRecordRepository controlRecordRepository;
    private final AgencyRepository agencyRepository;
    private final ControlRecordMapper mapper;

    public ControlRecordServiceImpl(
            ControlRecordRepository controlRecordRepository,
            AgencyRepository agencyRepository,
            ControlRecordMapper mapper) {
        this.controlRecordRepository = controlRecordRepository;
        this.agencyRepository = agencyRepository;
        this.mapper = mapper;
    }

    @Override
    public ControlRecord getActive(Long agencyId) {
        return controlRecordRepository.findByAgencyIdAndActiveTrue(agencyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Esta agencia no tiene parametros de compra (ControlRecord) configurados todavia"));
    }

    @Override
    public List<ControlRecordResponse> list() {
        return controlRecordRepository.findAll().stream().map(mapper::toResponse).toList();
    }

    @Override
    public ControlRecordResponse getByAgency(Long agencyId) {
        return mapper.toResponse(getActive(agencyId));
    }

    @Override
    @Transactional
    public ControlRecordResponse create(Long agencyId, ControlRecordRequest request) {
        if (controlRecordRepository.existsByAgencyIdAndActiveTrue(agencyId)) {
            throw new BusinessRuleException(
                    "Esta agencia ya tiene un ControlRecord activo; use editar en vez de crear uno nuevo");
        }
        Agency agency = agencyRepository.findById(agencyId)
                .orElseThrow(() -> new ResourceNotFoundException("Agencia no encontrada"));

        ControlRecord controlRecord = new ControlRecord();
        controlRecord.setAgency(agency);
        controlRecord.setActive(true);
        applyRequest(controlRecord, request);

        return mapper.toResponse(controlRecordRepository.save(controlRecord));
    }

    @Override
    @Transactional
    public ControlRecordResponse update(Long agencyId, ControlRecordRequest request) {
        ControlRecord controlRecord = getActive(agencyId);
        applyRequest(controlRecord, request);
        return mapper.toResponse(controlRecordRepository.save(controlRecord));
    }

    private void applyRequest(ControlRecord controlRecord, ControlRecordRequest request) {
        controlRecord.setControlNumber(request.controlNumber());
        controlRecord.setBaseFactor(request.baseFactor());
        controlRecord.setBaseWithholding(request.baseWithholding());
        controlRecord.setBaseLoad(request.baseLoad());
        controlRecord.setWithholdingPercentage(request.withholdingPercentage());
        controlRecord.setBaseHusk(request.baseHusk());
        controlRecord.setAvgHuskPercentage(request.avgHuskPercentage());
        controlRecord.setPurchasePoint(request.purchasePoint());
        controlRecord.setPrefix(request.prefix());
        controlRecord.setCosts(request.costs());
        controlRecord.setSampleSize(request.sampleSize());
        controlRecord.setExcelsoKg(request.excelsoKg());
        controlRecord.setGreenCoffeePercentage(request.greenCoffeePercentage());
        controlRecord.setSpecialtyThreshold(request.specialtyThreshold());
        controlRecord.setAssociatePercentage(request.associatePercentage());
        controlRecord.setNonAssociateDiscount(request.nonAssociateDiscount());
        controlRecord.setTrustedId(request.trustedId());
        controlRecord.setDianResolution(request.dianResolution());
        controlRecord.setResolutionDate(request.resolutionDate());
        controlRecord.setResolutionFrom(request.resolutionFrom());
        controlRecord.setResolutionTo(request.resolutionTo());
        controlRecord.setValidity(request.validity());
    }
}
