import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../config/api.config';
import { Fund } from '../models/dry-coffee-purchase.model';
import {
  OtherCoffeeNextInvoiceNumber,
  OtherCoffeePurchaseCalculation,
  OtherCoffeePurchaseRequest,
  OtherCoffeePurchaseResponse,
  OtherCoffeeQualityPercentages,
  OtherCoffeeSpecialInfo,
} from '../models/other-coffee-purchase.model';

/** Unica responsabilidad: llamadas HTTP para el formulario Compras Cafés Otros (COMPRASESP). */
@Injectable({ providedIn: 'root' })
export class OtherCoffeePurchaseService {
  private readonly http = inject(HttpClient);

  /** Fondo real (RP o LF, Cuadro combinado37) - a diferencia de Verde/Pasilla, no es fijo. */
  funds(): Observable<Fund[]> {
    return this.http.get<Fund[]>(`${API_BASE_URL}/funds`);
  }

  create(request: OtherCoffeePurchaseRequest): Observable<OtherCoffeePurchaseResponse> {
    return this.http.post<OtherCoffeePurchaseResponse>(`${API_BASE_URL}/purchases/other-coffee`, request);
  }

  /** Recalcula la cascada completa sin persistir (pasos Castigo/Descuento Fro/Otros Desctos). */
  preview(request: OtherCoffeePurchaseRequest): Observable<OtherCoffeePurchaseCalculation> {
    return this.http.post<OtherCoffeePurchaseCalculation>(
      `${API_BASE_URL}/purchases/other-coffee/preview`,
      request,
    );
  }

  /** Factura: siguiente consecutivo dentro del rango autorizado (paso "Fondo"). */
  nextInvoiceNumber(): Observable<OtherCoffeeNextInvoiceNumber> {
    return this.http.get<OtherCoffeeNextInvoiceNumber>(`${API_BASE_URL}/purchases/other-coffee/next-invoice-number`);
  }

  /** Cod Prod + datos del anuncio vigente (paso "Especial"). */
  specialInfo(agencyId: number, fundId: number, specialType: string): Observable<OtherCoffeeSpecialInfo> {
    const params = `agencyId=${agencyId}&fundId=${fundId}&specialType=${encodeURIComponent(specialType)}`;
    return this.http.get<OtherCoffeeSpecialInfo>(`${API_BASE_URL}/purchases/other-coffee/special-info?${params}`);
  }

  /** Porcentajes independientes (pasos "Peso Tot Alm"/"Peso Tot Pasilla"/"Peso Alm Sana"). */
  qualityPercentages(
    totalStoredWeight?: number,
    defectiveStoredWeight?: number,
    healthyStoredWeight?: number,
  ): Observable<OtherCoffeeQualityPercentages> {
    const params = new URLSearchParams();
    if (totalStoredWeight !== undefined) {
      params.set('totalStoredWeight', String(totalStoredWeight));
    }
    if (defectiveStoredWeight !== undefined) {
      params.set('defectiveStoredWeight', String(defectiveStoredWeight));
    }
    if (healthyStoredWeight !== undefined) {
      params.set('healthyStoredWeight', String(healthyStoredWeight));
    }
    return this.http.get<OtherCoffeeQualityPercentages>(
      `${API_BASE_URL}/purchases/other-coffee/quality-percentages?${params.toString()}`,
    );
  }
}
