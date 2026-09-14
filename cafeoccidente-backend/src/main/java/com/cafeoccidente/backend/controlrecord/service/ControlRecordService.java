package com.cafeoccidente.backend.controlrecord.service;

import com.cafeoccidente.backend.controlrecord.entity.ControlRecord;

/** Unica responsabilidad: exponer los parametros generales activos de una agencia. */
public interface ControlRecordService {
    ControlRecord getActive(Long agencyId);
}
