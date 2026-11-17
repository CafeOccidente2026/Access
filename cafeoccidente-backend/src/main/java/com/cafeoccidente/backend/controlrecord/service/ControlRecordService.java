package com.cafeoccidente.backend.controlrecord.service;

import com.cafeoccidente.backend.controlrecord.dto.ControlRecordStampResponse;
import com.cafeoccidente.backend.controlrecord.dto.ControlRecordRequest;
import com.cafeoccidente.backend.controlrecord.dto.ControlRecordResponse;
import com.cafeoccidente.backend.controlrecord.dto.GreenCoffeeAppliedResponse;
import com.cafeoccidente.backend.controlrecord.dto.ResolutionAlertResponse;
import com.cafeoccidente.backend.controlrecord.dto.GreenCoffeeValuesRequest;
import com.cafeoccidente.backend.controlrecord.entity.ControlRecord;
import java.util.List;

/** Unica responsabilidad: exponer y administrar los parametros generales (RegControl) de cada agencia. */
public interface ControlRecordService {
    ControlRecord getActive(Long agencyId);

    List<ControlRecordResponse> list();

    ControlRecordResponse getByAgency(Long agencyId);

    /** Hallazgo #6: huella de los valores del Registro de Control de la agencia. */
    ControlRecordStampResponse stamp(Long agencyId);

    /** R1 (17-nov): resoluciones DIAN por vencer o vencidas; {@code agencyId} null = todas las agencias. */
    List<ResolutionAlertResponse> resolutionAlerts(Long agencyId);

    ControlRecordResponse create(Long agencyId, ControlRecordRequest request);

    ControlRecordResponse update(Long agencyId, ControlRecordRequest request);

    /** Los 5 valores de cafe verde para todas las agencias con Registro de Control activo. */
    GreenCoffeeAppliedResponse applyGreenCoffeeToAll(GreenCoffeeValuesRequest request);
}
