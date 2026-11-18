import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';

import { API_BASE_URL } from '../config/api.config';
import { DryCoffeePurchaseService } from './dry-coffee-purchase.service';

/** Q2/Q15: /quality-percentages recibe los dos pesos y, si los hay, fondo y especial para el umbral. */
describe('DryCoffeePurchaseService.qualityPercentages', () => {
  let http: HttpTestingController;
  const url = `${API_BASE_URL}/purchases/dry-coffee/quality-percentages`;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
  });

  it('sends fund and special only when both are known', () => {
    const service = TestBed.inject(DryCoffeePurchaseService);
    service.qualityPercentages(200, 10, 1, 'NESPRESSO - FTUSA').subscribe();
    service.qualityPercentages(200).subscribe();

    const withSpecial = http.expectOne(`${url}?totalStoredWeight=200&defectiveStoredWeight=10&fundId=1&specialType=NESPRESSO+-+FTUSA`);
    const withoutSpecial = http.expectOne(`${url}?totalStoredWeight=200`);
    // El servicio arma los parametros en la URL (URLSearchParams), no en HttpParams.
    const query = (url: string) => new URL(url, 'http://localhost').searchParams;
    expect(query(withSpecial.request.url).get('specialType')).toBe('NESPRESSO - FTUSA');
    expect(query(withSpecial.request.url).get('fundId')).toBe('1');
    expect(query(withoutSpecial.request.url).has('fundId')).toBe(false);
    expect(query(withoutSpecial.request.url).has('specialType')).toBe(false);
    withSpecial.flush({});
    withoutSpecial.flush({});
    http.verify();
  });
});
