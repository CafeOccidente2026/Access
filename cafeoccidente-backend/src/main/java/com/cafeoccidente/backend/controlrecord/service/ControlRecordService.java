package com.cafeoccidente.backend.controlrecord.service;

import com.cafeoccidente.backend.controlrecord.dto.ControlRecordRequest;
import com.cafeoccidente.backend.controlrecord.dto.ControlRecordResponse;
import com.cafeoccidente.backend.controlrecord.dto.GreenCoffeeAppliedResponse;
import com.cafeoccidente.backend.controlrecord.dto.GreenCoffeeValuesRequest;
import com.cafeoccidente.backend.controlrecord.entity.ControlRecord;
import java.util.List;

/** Unica responsabilidad: exponer y administrar los parametros generales (RegControl) de cada agencia. */
public interface ControlRecordService {
    ControlRecord getActive(Long agencyId);

    List<ControlRecordResponse> list();

    ControlRecordResponse getByAgency(Long agencyId);

    ControlRecordResponse create(Long agencyId, ControlRecordRequest request);

    ControlRecordResponse update(Long agencyId, ControlRecordRequest request);

    /** Los 5 valores de cafe verde para todas las agencias con Registro de Control activo. */
    GreenCoffeeAppliedResponse applyGreenCoffeeToAll(GreenCoffeeValuesRequest request);
}
