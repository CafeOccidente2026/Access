import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../config/api.config';
import { TableInfo, TablePage } from '../models/table-access.model';

/** Unica responsabilidad: consultar las tablas de "Acceso a Tablas" (RF-03), solo lectura. */
@Injectable({ providedIn: 'root' })
export class TableAccessService {
  private readonly http = inject(HttpClient);

  list(): Observable<TableInfo[]> {
    return this.http.get<TableInfo[]>(`${API_BASE_URL}/tables`);
  }

  /** offset, limit, sort, dir, q, agency y filtros f.columna, f.columna.from, f.columna.to; el servidor hace todo. */
  rows(table: string, params: Record<string, string>): Observable<TablePage> {
    return this.http.get<TablePage>(`${API_BASE_URL}/tables/${table}`, { params });
  }
}
