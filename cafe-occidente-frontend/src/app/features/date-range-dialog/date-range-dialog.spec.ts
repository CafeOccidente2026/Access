import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { routes } from '../../app.routes';
import { API_BASE_URL } from '../../core/config/api.config';
import { ContentService } from '../../core/services/content.service';
import { NavigationService } from '../../core/services/navigation.service';
import content from '../../../../public/assets/data/date-range-dialog.json';
import menu from '../../../../public/assets/data/purchases-menu.json';
import { DateRangeDialogComponent } from './date-range-dialog';

const URL = `${API_BASE_URL}/purchases/reports/by-date`;
const ROW = {
  module: 'DRY', invoiceNumber: 1, purchaseDate: '2026-10-01', agencyName: 'Buesaco 1', fundCode: 'RP', prefix: 'SDBU',
  specialType: 'RN', productCode: '1', productName: null, announcementNumber: 'SDBU-1', idNumber: '1', firstName: 'A',
  lastName: null, greenKg: null, netKg: 100, factor: 90, unitPrice: 1, grossValue: 1, inventoryValue: 1,
  associateContribution: 0, cooperativeDiscount: 0, freightDiscount: 0, withholding: 0, otherDiscounts: 0, netToPay: 1,
  paymentMethod: 'EFECTIVO', status: 'VALIDA',
};

describe('DateRangeDialogComponent (HU-27)', () => {
  let http: HttpTestingController;
  const goTo = vi.fn();

  beforeEach(() => {
    goTo.mockReset();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ContentService, useValue: { loadJson: () => of(content) } },
        { provide: NavigationService, useValue: { goTo } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  function render() {
    const fixture = TestBed.createComponent(DateRangeDialogComponent);
    fixture.detectChanges();
    return { fixture, component: fixture.componentInstance, el: fixture.nativeElement as HTMLElement };
  }

  /** Corre los efectos y el recurso; despues de un flush deja resolver la respuesta. */
  async function settle(): Promise<void> {
    await Promise.resolve();
    TestBed.tick();
  }

  it('RN-25.1: Desde and Hasta are calendar fields, and Aceptar waits for both', () => {
    const { component, el } = render();
    expect((el.querySelector('[data-testid="from"]') as HTMLInputElement).type).toBe('date');
    expect((el.querySelector('[data-testid="to"]') as HTMLInputElement).type).toBe('date');
    expect(component.canAccept()).toBe(false);
    component.fromDate.set('2026-10-01');
    expect(component.canAccept()).toBe(false);
    component.onAccept();
    http.expectNone(() => true);
  });

  it('does not accept a Desde after the Hasta and says why', () => {
    const { fixture, component, el } = render();
    component.fromDate.set('2026-10-29');
    component.toDate.set('2026-10-01');
    fixture.detectChanges();
    expect(component.canAccept()).toBe(false);
    expect(el.querySelector('[data-testid="message"]')?.textContent).toContain(content.messages.fromAfterTo);
  });

  it('CA-1: Aceptar generates the report of the chosen range', async () => {
    const { component } = render();
    component.fromDate.set('2026-10-01');
    component.toDate.set('2026-10-29');
    component.onAccept();
    TestBed.tick();

    http.expectOne(`${URL}?from=2026-10-01&to=2026-10-29`).flush([ROW]);
    await settle();

    expect(component.doc()).not.toBeNull();
    expect(component.message()).toBeNull();
  });

  it('accepting the same range again asks the server again', async () => {
    const { component } = render();
    component.fromDate.set('2026-10-01');
    component.toDate.set('2026-10-01');
    component.onAccept();
    TestBed.tick();
    http.expectOne(() => true).flush([]);
    await settle();
    expect(component.message()).toBe(content.messages.empty);

    component.onAccept();
    TestBed.tick();
    http.expectOne(() => true).flush(null, { status: 500, statusText: 'Error' });
    await settle();
    expect(component.message()).toBe(content.messages.error);
    expect(component.doc()).toBeNull();
  });

  it('Cancelar goes back to the Compras menu', () => {
    const { component } = render();
    component.onCancel();
    expect(goTo).toHaveBeenCalledWith('/compras');
  });

  it('the Compras menu opens it for the Administrator and the User', () => {
    expect(menu.options.find((o) => o.label === 'Consulta compras por fecha')?.route).toBe('/compras/dialogo-fechas');
    const shell = routes.find((r) => r.children?.some((c) => c.path === 'compras/dialogo-fechas'));
    expect(shell?.children?.find((c) => c.path === 'compras/dialogo-fechas')?.data?.['roles']).toEqual(['ADMINISTRADOR', 'USUARIO']);
  });
});
