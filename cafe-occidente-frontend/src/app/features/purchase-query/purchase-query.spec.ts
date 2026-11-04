import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import pdfMake from 'pdfmake/build/pdfmake';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { routes } from '../../app.routes';
import { API_BASE_URL } from '../../core/config/api.config';
import { AnnulmentCandidate } from '../../core/models/purchase-annulment.model';
import { ContentService } from '../../core/services/content.service';
import content from '../../../../public/assets/data/purchase-query.json';
import menu from '../../../../public/assets/data/purchases-menu.json';
import { PurchaseQueryComponent } from './purchase-query';
import { DRY, GREEN, detail } from './purchase-query.testing';

const URL = `${API_BASE_URL}/purchases/query`;
const candidate = (over: Partial<AnnulmentCandidate>): AnnulmentCandidate => ({
  module: 'DRY', id: 1225, agencyId: 1, agencyName: 'Buesaco 1', prefix: 'SDBU', invoiceNumber: 27869,
  purchaseDate: '2026-10-29', fundCode: 'RP', idNumber: '990006003', firstName: 'PRUEBA', lastName: null,
  specialType: 'RN', netKg: 1000, netToPay: 12255579, paymentMethod: 'MIXTO', status: 'VALIDA', annulmentLocked: false,
  annulledAt: null, ...over,
});
const FIRST = candidate({});
const SECOND = candidate({ module: 'GREEN', id: 44, invoiceNumber: 27875, specialType: 'CV' });

describe('PurchaseQueryComponent (HU-28)', () => {
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
    const fixture = TestBed.createComponent(PurchaseQueryComponent);
    fixture.detectChanges();
    return { fixture, component: fixture.componentInstance, el: fixture.nativeElement as HTMLElement };
  }

  /** Corre los efectos y los recursos; despues de un flush deja resolver la respuesta. */
  async function settle(): Promise<void> {
    await Promise.resolve();
    TestBed.tick();
  }

  /** Muestra la compra que devuelva el servidor para una peticion de lista, y su detalle. */
  async function show(listUrl: string, found: AnnulmentCandidate, shown = detail()): Promise<void> {
    TestBed.tick();
    http.expectOne(listUrl).flush([found]);
    await settle();
    http.expectOne(`${URL}/${found.module}/${found.id}`).flush(shown);
    await settle();
  }

  const shownInvoice = (component: PurchaseQueryComponent) =>
    component.view()?.identificationFields.find((f) => f.key === 'invoiceNumber')?.value;

  it('CA-1: walks the purchases one by one with all their data, read only', async () => {
    const { component } = render();
    component.go('FIRST');
    await show(`${URL}/navigate?direction=FIRST`, FIRST);
    expect(shownInvoice(component)).toBe('27869');
    expect(component.view()?.paymentLocked).toBe(true);

    component.go('NEXT');
    await show(`${URL}/navigate?direction=NEXT&from=27869`, SECOND, detail({ module: 'GREEN', purchase: GREEN }));
    expect(shownInvoice(component)).toBe('27875');
  });

  it('at the end it stays on the purchase it was showing and says so', async () => {
    const { component } = render();
    component.go('LAST');
    await show(`${URL}/navigate?direction=LAST`, FIRST);

    component.go('NEXT');
    TestBed.tick();
    http.expectOne(`${URL}/navigate?direction=NEXT&from=27869`).flush([]);
    await settle();

    expect(shownInvoice(component)).toBe('27869');
    expect(component.message()).toBe(content.messages.noMore);
  });

  it('CA-2: the invoice search shows that purchase directly', async () => {
    const { component } = render();
    component.search(new Event('submit'), 'SDBU 27869');
    await show(`${URL}?invoiceNumber=27869`, FIRST);
    expect(shownInvoice(component)).toBe('27869');
  });

  it('says so when no purchase has that invoice', async () => {
    const { component } = render();
    component.search(new Event('submit'), '1');
    TestBed.tick();
    http.expectOne(`${URL}?invoiceNumber=1`).flush([]);
    await settle();
    expect(component.message()).toBe(content.messages.notFound);
    expect(component.view()).toBeNull();
  });

  it('RN-26.5: shows ESTADO ANULADA', async () => {
    const { component } = render();
    component.go('FIRST');
    await show(`${URL}/navigate?direction=FIRST`, FIRST, detail({ status: 'ANULADA' }));
    expect(component.view()?.statusField?.value).toBe('ANULADA');
  });

  it('CA-3: Reimprimir Dcto Equiv reprints the invoice of the purchase shown, with ANULADA if it was annulled', async () => {
    const open = vi.fn();
    const createPdf = vi.spyOn(pdfMake, 'createPdf').mockReturnValue({ open } as unknown as ReturnType<typeof pdfMake.createPdf>);
    const { component } = render();
    component.go('FIRST');
    await show(`${URL}/navigate?direction=FIRST`, FIRST, detail({ status: 'ANULADA', purchase: DRY }));

    component.reprint();

    await vi.waitFor(() => expect(open).toHaveBeenCalledTimes(1));
    const doc = createPdf.mock.calls[0][0];
    expect(JSON.stringify(doc.content)).toContain('SDBU - 27.869');
    expect(doc.watermark).toMatchObject({ text: content.annulledMark });
    createPdf.mockRestore();
  });

  it('says "No se pudo generar la factura" when the reprint PDF fails', async () => {
    const createPdf = vi.spyOn(pdfMake, 'createPdf').mockImplementation(() => {
      throw new Error('pdfmake');
    });
    const { component } = render();
    component.go('FIRST');
    await show(`${URL}/navigate?direction=FIRST`, FIRST);

    component.reprint();

    await vi.waitFor(() => expect(component.message()).toBe(content.messages.invoiceError));
    createPdf.mockRestore();
  });

  it('RF-26: the Compras menu opens it for the Administrator and the User', () => {
    expect(menu.options.find((o) => o.label === 'Consultar Compras')?.route).toBe('/compras/consulta');
    const shell = routes.find((r) => r.children?.some((c) => c.path === 'compras/consulta'));
    expect(shell?.children?.find((c) => c.path === 'compras/consulta')?.data?.['roles']).toEqual(['ADMINISTRADOR', 'USUARIO']);
  });
});
