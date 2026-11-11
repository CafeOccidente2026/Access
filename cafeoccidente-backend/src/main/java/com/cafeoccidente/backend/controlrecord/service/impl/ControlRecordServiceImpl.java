package com.cafeoccidente.backend.controlrecord.service.impl;

import com.cafeoccidente.backend.common.exception.BusinessRuleException;
import com.cafeoccidente.backend.common.exception.ResourceNotFoundException;
import com.cafeoccidente.backend.common.security.SecurityUtils;
import com.cafeoccidente.backend.controlrecord.dto.ControlRecordRequest;
import com.cafeoccidente.backend.controlrecord.dto.ControlRecordResponse;
import com.cafeoccidente.backend.controlrecord.dto.GreenCoffeeAppliedResponse;
import com.cafeoccidente.backend.controlrecord.dto.GreenCoffeeValuesRequest;
import com.cafeoccidente.backend.controlrecord.entity.ControlRecord;
import com.cafeoccidente.backend.controlrecord.mapper.ControlRecordMapper;
import com.cafeoccidente.backend.controlrecord.repository.ControlRecordRepository;
import com.cafeoccidente.backend.controlrecord.service.ControlRecordService;
import com.cafeoccidente.backend.history.service.ChangeHistoryService;
import com.cafeoccidente.backend.history.service.ChangeHistoryService.Action;
import com.cafeoccidente.backend.history.service.ChangeHistoryService.Entity;
import com.cafeoccidente.backend.history.service.Snapshots;
import com.cafeoccidente.backend.purchases.shared.entity.Agency;
import com.cafeoccidente.backend.purchases.shared.repository.AgencyRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ControlRecordServiceImpl implements ControlRecordService {

    private final ControlRecordRepository controlRecordRepository;
    private final AgencyRepository agencyRepository;
    private final ControlRecordMapper mapper;
    private final ChangeHistoryService history;
    private final SecurityUtils securityUtils;

    /**
     * Respuesta del area (11-nov): estos valores solo los cambia el Administrador. El % pasilla promedio se cambiaba en
     * Access para favorecer compras; el Usuario con el permiso temporal de la pantalla cambia el resto.
     */
    private static final List<Map.Entry<Function<ControlRecord, BigDecimal>, Function<ControlRecordRequest, BigDecimal>>>
            ADMIN_ONLY = List.of(
                    Map.entry(ControlRecord::getAvgHuskPercentage, ControlRecordRequest::avgHuskPercentage),
                    Map.entry(ControlRecord::getAssociatePercentage, ControlRecordRequest::associatePercentage),
                    Map.entry(ControlRecord::getNonAssociateDiscount, ControlRecordRequest::nonAssociateDiscount),
                    Map.entry(ControlRecord::getGreenTotalStoredWeight, ControlRecordRequest::greenTotalStoredWeight),
                    Map.entry(ControlRecord::getGreenTotalHuskWeight, ControlRecordRequest::greenTotalHuskWeight),
                    Map.entry(ControlRecord::getGreenHealthyStoredWeight, ControlRecordRequest::greenHealthyStoredWeight),
                    Map.entry(ControlRecord::getGreenHealthyUnitPrice, ControlRecordRequest::greenHealthyUnitPrice),
                    Map.entry(ControlRecord::getGreenDefectiveUnitPrice, ControlRecordRequest::greenDefectiveUnitPrice));

    public ControlRecordServiceImpl(
            ControlRecordRepository controlRecordRepository,
            AgencyRepository agencyRepository,
            ControlRecordMapper mapper,
            ChangeHistoryService history,
            SecurityUtils securityUtils) {
        this.controlRecordRepository = controlRecordRepository;
        this.agencyRepository = agencyRepository;
        this.mapper = mapper;
        this.history = history;
        this.securityUtils = securityUtils;
    }

    @Override
    public ControlRecord getActive(Long agencyId) {
        return controlRecordRepository.findByAgencyIdAndActiveTrue(agencyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "La agencia no tiene Registro de Control; pídale al Administrador que lo configure"));
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
                    "Esta agencia ya tiene un Registro de Control activo; use editar en vez de crear uno nuevo");
        }
        Agency agency = agencyRepository.findById(agencyId)
                .orElseThrow(() -> new ResourceNotFoundException("Agencia no encontrada"));

        ControlRecord controlRecord = new ControlRecord();
        controlRecord.setAgency(agency);
        controlRecord.setActive(true);
        applyRequest(controlRecord, request);

        ControlRecordResponse created = mapper.toResponse(controlRecordRepository.save(controlRecord));
        history.record(Entity.REGISTRO_CONTROL, created.id(), Action.CREAR, agencyId, null, snapshot(created));
        return created;
    }

    @Override
    @Transactional
    public ControlRecordResponse update(Long agencyId, ControlRecordRequest request) {
        ControlRecord controlRecord = getActive(agencyId);
        requireAdminForBusinessValues(controlRecord, request);
        Map<String, String> before = snapshot(mapper.toResponse(controlRecord));
        applyRequest(controlRecord, request);
        ControlRecordResponse updated = mapper.toResponse(controlRecordRepository.save(controlRecord));
        history.record(Entity.REGISTRO_CONTROL, updated.id(), Action.MODIFICAR, agencyId, before, snapshot(updated));
        return updated;
    }

    /**
     * Area de compras, 2026-10-26: los 5 valores de cafe verde se aplican a todos los Registros de Control
     * activos a la vez (para una sola agencia esta update()). Una fila de historial por agencia que cambio.
     */
    @Override
    @Transactional
    public GreenCoffeeAppliedResponse applyGreenCoffeeToAll(GreenCoffeeValuesRequest request) {
        List<ControlRecord> records = controlRecordRepository.findByActiveTrue();
        for (ControlRecord controlRecord : records) {
            Map<String, String> before = snapshot(mapper.toResponse(controlRecord));
            controlRecord.setGreenTotalStoredWeight(request.greenTotalStoredWeight());
            controlRecord.setGreenTotalHuskWeight(request.greenTotalHuskWeight());
            controlRecord.setGreenHealthyStoredWeight(request.greenHealthyStoredWeight());
            controlRecord.setGreenHealthyUnitPrice(request.greenHealthyUnitPrice());
            controlRecord.setGreenDefectiveUnitPrice(request.greenDefectiveUnitPrice());
            ControlRecordResponse updated = mapper.toResponse(controlRecordRepository.save(controlRecord));
            history.record(Entity.REGISTRO_CONTROL, updated.id(), Action.MODIFICAR, updated.agencyId(), before,
                    snapshot(updated));
        }
        return new GreenCoffeeAppliedResponse(records.size());
    }

    private void requireAdminForBusinessValues(ControlRecord current, ControlRecordRequest request) {
        boolean changed = ADMIN_ONLY.stream().anyMatch(field ->
                field.getKey().apply(current).compareTo(field.getValue().apply(request)) != 0);
        if (changed && !securityUtils.isAdmin()) {
            throw new BusinessRuleException("Solo el Administrador puede cambiar este valor");
        }
    }

    private static Map<String, String> snapshot(ControlRecordResponse record) {
        return Snapshots.of(record, "id", "agencyId", "agencyName");
    }

    /** RN-06.3: el punto de compra es el nombre de la agencia en Access (o su nombre si no viene de Access). */
    private static String purchasePoint(Agency agency) {
        String accessName = agency.getAccessAgencyValue();
        return accessName == null || accessName.isBlank() ? agency.getName() : accessName;
    }

    private void applyRequest(ControlRecord controlRecord, ControlRecordRequest request) {
        controlRecord.setControlNumber(request.controlNumber());
        controlRecord.setBaseFactor(request.baseFactor());
        controlRecord.setBaseWithholding(request.baseWithholding());
        controlRecord.setBaseLoad(request.baseLoad());
        controlRecord.setWithholdingPercentage(request.withholdingPercentage());
        controlRecord.setBaseHusk(request.baseHusk());
        controlRecord.setAvgHuskPercentage(request.avgHuskPercentage());
        controlRecord.setPurchasePoint(purchasePoint(controlRecord.getAgency()));
        controlRecord.setPrefix(request.prefix());
        controlRecord.setCosts(request.costs());
        controlRecord.setSampleSize(request.sampleSize());
        controlRecord.setExcelsoKg(request.excelsoKg());
        controlRecord.setGreenCoffeePercentage(request.greenCoffeePercentage());
        controlRecord.setAssociatePercentage(request.associatePercentage());
        controlRecord.setNonAssociateDiscount(request.nonAssociateDiscount());
        controlRecord.setTrustedId(request.trustedId());
        controlRecord.setDianResolution(request.dianResolution());
        controlRecord.setResolutionDate(request.resolutionDate());
        controlRecord.setResolutionFrom(request.resolutionFrom());
        controlRecord.setResolutionTo(request.resolutionTo());
        controlRecord.setValidity(request.validity());
        controlRecord.setSpecialtyThreshold(request.specialtyThreshold());
        controlRecord.setGreenTotalStoredWeight(request.greenTotalStoredWeight());
        controlRecord.setGreenTotalHuskWeight(request.greenTotalHuskWeight());
        controlRecord.setGreenHealthyStoredWeight(request.greenHealthyStoredWeight());
        controlRecord.setGreenHealthyUnitPrice(request.greenHealthyUnitPrice());
        controlRecord.setGreenDefectiveUnitPrice(request.greenDefectiveUnitPrice());
    }
}
