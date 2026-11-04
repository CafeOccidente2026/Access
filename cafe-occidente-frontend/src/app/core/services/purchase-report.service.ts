import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../config/api.config';
import { PurchaseReportRow } from '../models/purchase-report.model';

/** Unica responsabilidad: reportes de Compras (/api/purchases/reports) de la agencia de trabajo. */
@Injectable({ providedIn: 'root' })
export class PurchaseReportService {
  private readonly http = inject(HttpClient);

  /** Compras entre dos fechas (yyyy-MM-dd), ambas incluidas. */
  byDate(from: string, to: string): Observable<PurchaseReportRow[]> {
    return this.http.get<PurchaseReportRow[]>(`${API_BASE_URL}/purchases/reports/by-date`, { params: { from, to } });
  }
}
