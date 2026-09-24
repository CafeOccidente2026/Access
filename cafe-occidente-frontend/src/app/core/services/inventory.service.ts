import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../config/api.config';
import { InventoryMovementResponse, RemissionRequest, RemissionResponse } from '../models/inventory.model';

/** Unica responsabilidad: llamadas HTTP para Inventarios (movimientos, solo lectura) y Remisiones
 *  (Salidas). */
@Injectable({ providedIn: 'root' })
export class InventoryService {
  private readonly http = inject(HttpClient);

  listMovements(agencyId: number): Observable<InventoryMovementResponse[]> {
    return this.http.get<InventoryMovementResponse[]>(`${API_BASE_URL}/inventory-movements?agencyId=${agencyId}`);
  }

  createRemission(request: RemissionRequest): Observable<RemissionResponse> {
    return this.http.post<RemissionResponse>(`${API_BASE_URL}/remissions`, request);
  }
}
