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

const URL = `${API_BASE_URL}/purchases/reports/announcement`;
const ROW = {
  module: 'DRY', invoiceNumber: 1, purchaseDate: '2026-05-09', agencyName: 'EL TAMBO', fundCode: 'LF', prefix: 'SDTA',
  specialType: 'RN', productCode: '0110001000008', productName: null, announcementNumber: 'SDTA-1', idNumber: '1',
  firstName: 'A', lastName: null, greenKg: null, netKg: 100, factor: 90, unitPrice: 1, grossValue: 1, inventoryValue: 1,
  associateContribution: 0, cooperativeDiscount: 0, freightDiscount: 0, withholding: 0, otherDiscounts: 0, netToPay: 1,
  paymentMethod: 'EFECTIVO', status: 'VALIDA', announcementDate: '2026-05-09',
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

  /** Corre los efectos y el recurso; despues de un flush deja resolver la respuesta. */
  async function settle(): Promise<void> {
    await Promise.resolve();
    TestBed.tick();
  }

  it('starts with no announcement, Desde and Hasta on today, Ambos, and asks nothing until Generar', () => {
    const { component, el } = render();
    const today = new Date().toLocaleDateString('en-CA');
    expect([component.announcement(), component.fromDate(), component.toDate(), component.kind()]).toEqual(['', today, today, 'both']);
    expect(el.querySelector('[data-testid="announcement"]')).not.toBeNull();
    expect(el.querySelector<HTMLSelectElement>('[data-testid="kind"]')!.options.length).toBe(3);
    http.expectNone(() => true);
  });

  it('prueba del 19-nov: with the announcement and no dates it asks that announcement of any date, like Access', () => {
    const { component } = render();
    component.announcement.set('3590');
    component.fromDate.set('');
    component.toDate.set('');
    component.generate();
    TestBed.tick();

    const request = http.expectOne((r) => r.url === URL);
    expect(request.request.params.keys()).toEqual(['announcement']);
    expect(request.request.params.get('announcement')).toBe('3590');
  });

  it('without the announcement it asks every announcement of the range', () => {
    const { component } = render();
    const from = '2026-05-09';
    const to = '2026-05-12';
    component.fromDate.set(from);
    component.toDate.set(to);
    component.generate();
    TestBed.tick();

    http.expectOne(`${URL}?from=${from}&to=${to}`);
  });

  it('Ambos builds the summary and the detail as two separate documents; Resumen or Detallado only one', async () => {
    const { component } = render();
    component.generate();
    TestBed.tick();
    http.expectOne(() => true).flush([ROW]);
    await settle();
    expect(component.jobs().summary).toMatchObject({ kind: 'announcementSummary', args: [[ROW], content.pdf] });
    expect(component.jobs().detail).toMatchObject({ kind: 'announcementDetail', args: [[ROW], content.pdf] });

    component.kind.set('detail');
    component.generate();
    TestBed.tick();
    http.expectOne(() => true).flush([ROW]);
    await settle();
    expect(component.jobs().summary).toBeNull();
    expect(component.jobs().detail).not.toBeNull();

    component.kind.set('summary');
    component.generate();
    TestBed.tick();
    http.expectOne(() => true).flush([ROW]);
    await settle();
    expect(component.jobs().summary).not.toBeNull();
    expect(component.jobs().detail).toBeNull();
  });

  it('without the announcement Desde and Hasta are required and Desde cannot be after Hasta', () => {
    const { component } = render();
    component.fromDate.set('');
    component.generate();
    expect(component.message()).toBe('Indique Desde Fecha y Hasta Fecha (Desde no puede ser posterior a Hasta)');

    component.fromDate.set('2026-05-12');
    component.toDate.set('2026-05-09');
    component.generate();
    expect(component.message()).toBe('Indique Desde Fecha y Hasta Fecha (Desde no puede ser posterior a Hasta)');
    http.expectNone(() => true);
  });

  it('says so when there are no purchases (of the range or of the announcement) and when the server fails', async () => {
    const { component } = render();
    component.generate();
    TestBed.tick();
    http.expectOne(() => true).flush([]);
    await settle();
    expect(component.message()).toBe('No hay compras para esas fechas');
    expect(component.jobs()).toEqual({ summary: null, detail: null });

    component.announcement.set('77');
    component.generate();
    TestBed.tick();
    http.expectOne(() => true).flush([]);
    await settle();
    expect(component.message()).toBe('No hay compras con el anuncio 77');

    component.generate();
    TestBed.tick();
    http.expectOne(() => true).flush(null, { status: 500, statusText: 'Error' });
    await settle();
    expect(component.message()).toBe(content.messages.error);
  });

  it('cierre de año: the range can cross the year (28-dic to 3-ene)', () => {
    const { component, el } = render();
    const year = new Date().getFullYear();
    component.fromDate.set(`${year - 1}-12-28`);
    component.toDate.set(`${year}-01-03`);
    component.generate();
    TestBed.tick();

    expect(component.message()).toBeNull();
    const request = http.expectOne((r) => r.url === URL);
    expect([request.request.params.get('from'), request.request.params.get('to')]).toEqual([`${year - 1}-12-28`, `${year}-01-03`]);
    expect(el.querySelector('[data-testid="from"]')!.getAttribute('min')).toBeNull();
  });

  it('each document has its own Imprimir, shown only once it is ready', () => {
    const { el } = render();
    expect(el.querySelector('[data-testid="print-summary"]')).toBeNull();
    expect(el.querySelector('[data-testid="print-detail"]')).toBeNull();
    expect([content.printSummaryLabel, content.printDetailLabel]).toEqual(['Imprimir resumen', 'Imprimir detallado']);
  });

  it('Q14: the Administrator and the User can open it', () => {
    const shell = routes.find((r) => r.children?.some((c) => c.path === 'compras/reporte-anuncio'));
    const roles = shell?.children?.find((c) => c.path === 'compras/reporte-anuncio')?.data?.['roles'];
    expect(roles).toEqual(['ADMINISTRADOR', 'USUARIO']);
  });
});
