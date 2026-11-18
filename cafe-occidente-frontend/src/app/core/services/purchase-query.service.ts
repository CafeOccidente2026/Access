import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../config/api.config';
import { InvoiceListService } from './invoice-list.service';
import { AnnulmentModule } from '../models/purchase-annulment.model';
import { PurchaseQueryDetail } from '../models/purchase-query.model';

/** Unica responsabilidad: "Consultar Compras" (/api/purchases/query) de la agencia de trabajo. */
@Injectable({ providedIn: 'root' })
export class PurchaseQueryService extends InvoiceListService {
  protected readonly url = `${API_BASE_URL}/purchases/query`;

  detail(module: AnnulmentModule, id: number): Observable<PurchaseQueryDetail> {
    return this.http.get<PurchaseQueryDetail>(`${this.url}/${module}/${id}`);
  }
}
