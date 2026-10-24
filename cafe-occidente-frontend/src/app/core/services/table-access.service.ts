import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../config/api.config';
import { QueryableTable, TablePage } from '../models/table-access.model';

/** Unica responsabilidad: consultar las tablas de "Acceso a Tablas" (RF-03), solo lectura. */
@Injectable({ providedIn: 'root' })
export class TableAccessService {
  private readonly http = inject(HttpClient);

  list(): Observable<QueryableTable[]> {
    return this.http.get<QueryableTable[]>(`${API_BASE_URL}/tables`);
  }

  page(table: QueryableTable, page: number, size: number): Observable<TablePage> {
    return this.http.get<TablePage>(`${API_BASE_URL}/tables/${table}`, { params: { page, size } });
  }
}
