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

  nessQuotaBalances(agencyId: number | null): Observable<NessQuotaBalanceRow[]> {
    return this.http.get<NessQuotaBalanceRow[]>(`${this.url}/ness-quota-balances`, { params: toParams({ agencyId }) });
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
