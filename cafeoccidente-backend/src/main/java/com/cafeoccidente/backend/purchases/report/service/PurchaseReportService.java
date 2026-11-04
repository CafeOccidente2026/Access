package com.cafeoccidente.backend.purchases.report.service;

import com.cafeoccidente.backend.purchases.report.dto.PurchaseReportRow;
import java.time.LocalDate;
import java.util.List;

/** Reportes de Compras de la agencia de trabajo (RF-05.1): Reporte Anuncio (RF-24) y Compras por fecha (RF-25). */
public interface PurchaseReportService {

    /** Las compras entre dos fechas, ambas incluidas; rechaza Desde posterior a Hasta. */
    List<PurchaseReportRow> byDate(LocalDate from, LocalDate to);
}
