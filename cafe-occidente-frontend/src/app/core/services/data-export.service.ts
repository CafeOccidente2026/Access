import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../config/api.config';

/** Unica responsabilidad: "Exportar Informacion" / "Exportado Especial" (/api/data-export, solo ADMIN). */
@Injectable({ providedIn: 'root' })
export class DataExportService {
  private readonly http = inject(HttpClient);

  /** POST: como en Access, marca como exportado lo que devuelve. endpoint '' o 'special'. */
  export(endpoint: string, params: Record<string, string | number | null>): Observable<HttpResponse<Blob>> {
    let httpParams = new HttpParams();
    for (const [key, value] of Object.entries(params)) {
      if (value !== null && value !== '') {
        httpParams = httpParams.set(key, value);
      }
    }
    const url = `${API_BASE_URL}/data-export${endpoint ? `/${endpoint}` : ''}`;
    return this.http.post(url, null, { params: httpParams, observe: 'response', responseType: 'blob' });
  }
}
