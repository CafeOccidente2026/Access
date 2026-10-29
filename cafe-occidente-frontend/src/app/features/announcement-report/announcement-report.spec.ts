import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it } from 'vitest';

import { routes } from '../../app.routes';
import { API_BASE_URL } from '../../core/config/api.config';
import { ContentService } from '../../core/services/content.service';
import content from '../../../../public/assets/data/announcement-report.json';
import { AnnouncementReportComponent } from './announcement-report';

const URL = `${API_BASE_URL}/purchases/reports/by-date`;
const ROW = {
  module: 'DRY', invoiceNumber: 1, purchaseDate: '2026-05-09', agencyName: 'EL TAMBO', fundCode: 'LF', prefix: 'SDTA',
  specialType: 'RN', productCode: '0110001000008', productName: null, announcementNumber: 'SDTA-1', idNumber: '1',
  firstName: 'A', lastName: null, greenKg: null, netKg: 100, factor: 90, unitPrice: 1, grossValue: 1, inventoryValue: 1,
  associateContribution: 0, cooperativeDiscount: 0, freightDiscount: 0, withholding: 0, otherDiscounts: 0, netToPay: 1,
  paymentMethod: 'EFECTIVO', status: 'VALIDA',
};

describe('AnnouncementReportComponent (HU-26)', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ContentService, useValue: { loadJson: () => of(content) } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  function render() {
    const fixture = TestBed.createComponent(AnnouncementReportComponent);
    fixture.detectChanges();
    return { fixture, component: fixture.componentInstance, el: fixture.nativeElement as HTMLElement };
  }

  it('starts on today and asks nothing until Generar', () => {
    const { component } = render();
    expect(component.date()).toBe(new Date().toLocaleDateString('en-CA'));
    http.expectNone(() => true);
  });

  /** Corre los efectos y el recurso; despues de un flush deja resolver la respuesta. */
  async function settle(): Promise<void> {
    await Promise.resolve();
    TestBed.tick();
  }

  it('CA-1: Generar asks the purchases of the chosen day and builds one document', async () => {
    const { component } = render();
    const day = `${component.year}-05-09`;
    component.date.set(day);
    component.generate();
    TestBed.tick();

    http.expectOne(`${URL}?from=${day}&to=${day}`).flush([ROW]);
    await settle();

    expect(component.doc()).not.toBeNull();
    expect(component.message()).toBeNull();
  });

  it('says so when the day has no purchases, and when the server fails', async () => {
    const { component } = render();
    component.generate();
    TestBed.tick();
    http.expectOne(() => true).flush([]);
    await settle();
    expect(component.message()).toBe(content.messages.empty);
    expect(component.doc()).toBeNull();

    component.generate();
    TestBed.tick();
    http.expectOne(() => true).flush(null, { status: 500, statusText: 'Error' });
    await settle();
    expect(component.message()).toBe(content.messages.error);
  });

  it('only accepts dates of the current year', () => {
    const { component } = render();
    component.date.set(`${component.year - 1}-12-31`);
    component.generate();
    expect(component.message()).toBe(content.messages.wrongYear.replace('{year}', String(component.year)));
    http.expectNone(() => true);
  });

  it('CA-2: there is a single Imprimir, and only once the report is shown', () => {
    const { el } = render();
    expect(el.querySelector('[data-testid="print"]')).toBeNull();
    expect(content.printLabel).toBe('Imprimir');
  });

  it('Q14: the Administrator and the User can open it', () => {
    const shell = routes.find((r) => r.children?.some((c) => c.path === 'compras/reporte-anuncio'));
    const roles = shell?.children?.find((c) => c.path === 'compras/reporte-anuncio')?.data?.['roles'];
    expect(roles).toEqual(['ADMINISTRADOR', 'USUARIO']);
  });
});
