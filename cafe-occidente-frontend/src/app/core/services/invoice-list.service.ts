import { HttpClient } from '@angular/common/http';
import { inject } from '@angular/core';
import { Observable } from 'rxjs';

import { AnnulmentCandidate, AnnulmentDirection } from '../models/purchase-annulment.model';

/**
 * Buscar una factura y recorrer las compras |◄ ◄ ► ►| en la agencia de trabajo: igual en Anular Documento,
 * Consultar Compras y Corregir Compra; cada servicio pone su {@code url}.
 */
export abstract class InvoiceListService {
  protected readonly http = inject(HttpClient);
  protected abstract readonly url: string;

  findByInvoice(invoiceNumber: number): Observable<AnnulmentCandidate[]> {
    return this.http.get<AnnulmentCandidate[]>(this.url, { params: { invoiceNumber } });
  }

  /** La factura vecina de {@code from}; lista vacia si no hay mas en esa direccion. */
  navigate(from: number | null, direction: AnnulmentDirection): Observable<AnnulmentCandidate[]> {
    const params: Record<string, string | number> = { direction };
    if (from != null) {
      params['from'] = from;
    }
    return this.http.get<AnnulmentCandidate[]>(`${this.url}/navigate`, { params });
  }
}
