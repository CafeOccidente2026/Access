package com.cafeoccidente.backend.purchases.report.service;

import com.cafeoccidente.backend.purchases.report.dto.FutureBalanceRow;
import com.cafeoccidente.backend.purchases.report.dto.PurchaseReportRow;
import java.time.LocalDate;
import java.util.List;

/** Reportes de Compras de la agencia de trabajo (RF-05.1): Reporte Anuncio (RF-24) y Compras por fecha (RF-25). */
public interface PurchaseReportService {

    /** Las compras entre dos fechas, ambas incluidas; rechaza Desde posterior a Hasta. */
    List<PurchaseReportRow> byDate(LocalDate from, LocalDate to);

    /**
     * Informe Anuncios a Futuro (HU-34): las compras de cafe seco de la agencia de trabajo con el anuncio digitado
     * (su numero, sin prefijo) entre las dos fechas; las anuladas vienen en 0 y "ANULADA".
     */
    List<PurchaseReportRow> futureAnnouncement(int announcementNumber, LocalDate from, LocalDate to);

    /** Informe Saldo Anuncios (HU-35): las compras a futuro del anuncio digitado, con K Anun, K Entr y Saldo Kg. */
    List<FutureBalanceRow> futureBalance(int announcementNumber);
}
