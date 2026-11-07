import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../config/api.config';
import { FutureBalanceRow, PurchaseReportRow } from '../models/purchase-report.model';

/** Unica responsabilidad: reportes de Compras (/api/purchases/reports) de la agencia de trabajo. */
@Injectable({ providedIn: 'root' })
export class PurchaseReportService {
  private readonly http = inject(HttpClient);

  /** Compras entre dos fechas (yyyy-MM-dd), ambas incluidas. */
  byDate(from: string, to: string): Observable<PurchaseReportRow[]> {
    return this.http.get<PurchaseReportRow[]>(`${API_BASE_URL}/purchases/reports/by-date`, { params: { from, to } });
  }

  /** Informe Anuncios a Futuro (HU-34): compras de seco del anuncio (su número) entre dos fechas. */
  futureAnnouncement(announcement: number, from: string, to: string): Observable<PurchaseReportRow[]> {
    return this.http.get<PurchaseReportRow[]>(`${API_BASE_URL}/purchases/reports/future-announcement`, {
      params: { announcement, from, to },
    });
  }

  /** Informe Saldo Anuncios (HU-35): compras a futuro del anuncio (su número). */
  futureBalance(announcement: number): Observable<FutureBalanceRow[]> {
    return this.http.get<FutureBalanceRow[]>(`${API_BASE_URL}/purchases/reports/future-balance`, { params: { announcement } });
  }
}
