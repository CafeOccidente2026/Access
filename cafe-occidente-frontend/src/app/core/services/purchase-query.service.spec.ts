import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';

import { API_BASE_URL } from '../config/api.config';
import { PurchaseQueryService } from './purchase-query.service';

describe('PurchaseQueryService', () => {
  it('searches, walks and reads the detail of the purchases of the working agency', () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    const service = TestBed.inject(PurchaseQueryService);
    const http = TestBed.inject(HttpTestingController);
    const url = `${API_BASE_URL}/purchases/query`;

    service.findByInvoice(27869).subscribe();
    service.navigate(null, 'LAST').subscribe();
    service.navigate(27869, 'NEXT').subscribe();
    service.detail('GREEN', 43).subscribe();

    for (const u of [`${url}?invoiceNumber=27869`, `${url}/navigate?direction=LAST`, `${url}/navigate?direction=NEXT&from=27869`, `${url}/GREEN/43`]) {
      expect(http.expectOne(u).request.method).toBe('GET');
    }
  });
});
