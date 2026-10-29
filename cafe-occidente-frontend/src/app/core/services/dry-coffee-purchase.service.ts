import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../config/api.config';
import {
  DryCoffeePurchaseCalculation,
  DryCoffeePurchaseRequest,
  DryCoffeePurchaseResponse,
  Fund,
  NextInvoiceNumber,
  QualityPercentages,
  SpecialInfo,
} from '../models/dry-coffee-purchase.model';

/** Unica responsabilidad: llamadas HTTP para el formulario de Compras Café Seco. */
@Injectable({ providedIn: 'root' })
export class DryCoffeePurchaseService {
  private readonly http = inject(HttpClient);

  funds(): Observable<Fund[]> {
    return this.http.get<Fund[]>(`${API_BASE_URL}/funds`);
  }

  create(request: DryCoffeePurchaseRequest): Observable<DryCoffeePurchaseResponse> {
    return this.http.post<DryCoffeePurchaseResponse>(`${API_BASE_URL}/purchases/dry-coffee`, request);
  }

  /** Recalcula la cascada completa sin persistir (pasos Castigo/Descuento Fro/Otros Desctos). */
  preview(request: DryCoffeePurchaseRequest): Observable<DryCoffeePurchaseCalculation> {
    return this.http.post<DryCoffeePurchaseCalculation>(
      `${API_BASE_URL}/purchases/dry-coffee/preview`,
      request,
    );
  }

  /** Factura: siguiente consecutivo dentro del rango autorizado (paso "Fondo"). */
  nextInvoiceNumber(): Observable<NextInvoiceNumber> {
    return this.http.get<NextInvoiceNumber>(`${API_BASE_URL}/purchases/dry-coffee/next-invoice-number`);
  }

  /** Cod Prod + datos del anuncio vigente (paso "Especial"). */
  specialInfo(fundId: number, specialType: string): Observable<SpecialInfo> {
    const params = `fundId=${fundId}&specialType=${encodeURIComponent(specialType)}`;
    return this.http.get<SpecialInfo>(`${API_BASE_URL}/purchases/dry-coffee/special-info?${params}`);
  }

  /** Porc Merma con Peso Tot Alm; con los dos pesos, ademas Porc Kg Pas, Peso Alm Sana y Factor (Q2).
   *  Con fondo y especial, el servidor aplica tambien el umbral de especiales (Q15). */
  qualityPercentages(
    totalStoredWeight?: number,
    defectiveStoredWeight?: number,
    fundId?: number,
    specialType?: string,
  ): Observable<QualityPercentages> {
    const params = new URLSearchParams();
    if (totalStoredWeight !== undefined) {
      params.set('totalStoredWeight', String(totalStoredWeight));
    }
    if (defectiveStoredWeight !== undefined) {
      params.set('defectiveStoredWeight', String(defectiveStoredWeight));
    }
    if (fundId !== undefined && specialType) {
      params.set('fundId', String(fundId));
      params.set('specialType', specialType);
    }
    return this.http.get<QualityPercentages>(
      `${API_BASE_URL}/purchases/dry-coffee/quality-percentages?${params.toString()}`,
    );
  }
}
