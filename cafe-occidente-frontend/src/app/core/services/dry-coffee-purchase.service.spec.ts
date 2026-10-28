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

    http.expectOne(`${url}?totalStoredWeight=200&defectiveStoredWeight=10&fundId=1&specialType=NESPRESSO+-+FTUSA`).flush({});
    http.expectOne(`${url}?totalStoredWeight=200`).flush({});
    http.verify();
  });
});
