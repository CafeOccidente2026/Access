import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../config/api.config';
import {
  Associate,
  AssociatePage,
  BeneficiaryRow,
  NessQuotaBalanceRow,
  NessQuotaPage,
  NessQuotaUploadRecord,
  NessQuotaUploadResult,
  VendorCreateRequest,
} from '../models/vendor.model';

/** Unica responsabilidad: llamadas HTTP de "MENUS VENDEDORES" (/api/vendors). */
@Injectable({ providedIn: 'root' })
export class VendorService {
  private readonly http = inject(HttpClient);
  private readonly url = `${API_BASE_URL}/vendors`;

  create(request: VendorCreateRequest): Observable<Associate> {
    return this.http.post<Associate>(this.url, request);
  }

  associateAt(position: number): Observable<AssociatePage> {
    return this.http.get<AssociatePage>(`${this.url}/associates`, { params: { position } });
  }

  findAssociate(idNumber: string): Observable<AssociatePage> {
    return this.http.get<AssociatePage>(`${this.url}/associates/${encodeURIComponent(idNumber)}`);
  }

  beneficiary(params: Record<string, string | number | null>): Observable<BeneficiaryRow[]> {
    return this.http.get<BeneficiaryRow[]>(`${this.url}/reports/beneficiary`, { params: toParams(params) });
  }

  nessQuotas(idNumber: string, page: number): Observable<NessQuotaPage> {
    return this.http.get<NessQuotaPage>(`${this.url}/ness-quotas`, { params: toParams({ idNumber, page }) });
  }

  /** Saldos Cupos NESS (fuera del RF, Q5): el Administrador puede pedir todas las agencias (agencyId null). */
  nessQuotaBalances(agencyId: number | null): Observable<NessQuotaBalanceRow[]> {
    return this.http.get<NessQuotaBalanceRow[]>(`${this.url}/ness-quota-balances`, { params: toParams({ agencyId }) });
  }

  /** Actualiza Cupos NESS (HU-54): el archivo va tal cual en el cuerpo; el servidor lo lee y valida. */
  previewNessUpload(file: File): Observable<NessQuotaUploadResult> {
    return this.postFile(`${this.url}/ness-quotas/upload/preview`, file);
  }

  applyNessUpload(file: File): Observable<NessQuotaUploadResult> {
    return this.postFile(`${this.url}/ness-quotas/upload`, file);
  }

  nessUploads(): Observable<NessQuotaUploadRecord[]> {
    return this.http.get<NessQuotaUploadRecord[]>(`${this.url}/ness-quotas/uploads`);
  }

  private postFile(url: string, file: File): Observable<NessQuotaUploadResult> {
    return this.http.post<NessQuotaUploadResult>(url, file, {
      params: { fileName: file.name },
      headers: { 'Content-Type': 'application/octet-stream' },
    });
  }
}

/** Sin los vacios: el backend los toma como "todos". */
function toParams(values: Record<string, string | number | null>): HttpParams {
  let params = new HttpParams();
  for (const [key, value] of Object.entries(values)) {
    if (value !== null && value !== '') {
      params = params.set(key, value);
    }
  }
  return params;
}
