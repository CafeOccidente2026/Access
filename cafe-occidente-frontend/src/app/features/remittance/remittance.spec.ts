import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { API_BASE_URL } from '../../core/config/api.config';
import { Remittance, RemittanceFiles, RemittancePurchase } from '../../core/models/remittance.model';
import { AuthService } from '../../core/services/auth.service';
import { ContentService } from '../../core/services/content.service';
import content from '../../../../public/assets/data/remittance.json';
import { RemittanceComponent } from './remittance';

const URL = `${API_BASE_URL}/remittances`;

const purchase = (over: Partial<RemittancePurchase>): RemittancePurchase => ({
  module: 'DRY', id: 1, invoice: 'SDXX1001', purchaseDate: '2026-11-03', specialType: 'RN', idNumber: '900100200',
  sellerName: 'ANA PRUEBA', netKg: 300, grossValue: 3000000, ...over,
});
const P1 = purchase({});
const P2 = purchase({ module: 'HUSK', id: 7, invoice: 'SDXX1002', netKg: 200.5 });

const REMITTANCE: Remittance = {
  id: 9, number: 'SDXX-RP-0008', fundCode: 'RP', specialType: 'RN', packagingQuantity: 10, packagingType: 'USADO',
  status: 'VIGENTE', createdAt: '2026-11-03T14:00:00Z', purchases: 2, netKg: 500.5,
};
const FILES: RemittanceFiles = { number: 'SDXX-RP-0008', agencyName: 'PUNTO', generatedAt: '2026-11-03T09:00:00', lines: [] };

describe('RemittanceComponent (HU-13, HU-14)', () => {
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

  async function settle(): Promise<void> {
    await Promise.resolve();
    TestBed.tick();
    await Promise.resolve();
  }

  async function render() {
    const fixture = TestBed.createComponent(RemittanceComponent);
    fixture.detectChanges();
    await settle();
    http.match((r) => r.url === `${URL}/specials`).forEach((r) => r.flush(['RN', 'PASILLA']));
    await settle();
    return { fixture, component: fixture.componentInstance };
  }

  /** Aceptar con el fondo y el especial: responde las pendientes. */
  async function accept(component: RemittanceComponent, pending: RemittancePurchase[]) {
    component.accept();
    await settle();
    http.expectOne((r) => r.url === `${URL}/pending` && r.params.get('fundCode') === 'RP'
      && r.params.get('specialType') === 'RN').flush(pending);
    await settle();
  }

  it('RN-11.1 to 11.3: Aceptar lists the pending invoices of the fund and special; marking them adds their kilos', async () => {
    const { component } = await render();
    component.special.set('RN');

    await accept(component, [P1, P2]);
    expect(component.pending()).toEqual([P1, P2]);

    component.toggle(P1);
    component.toggle(P2);
    expect(component.accumulated()).toBe(500.5);
    component.toggle(P2);
    expect(component.accumulated()).toBe(300);
  });

  it('Genera Remesa: two clicks create a single remittance, which then offers its 4 files', async () => {
    const { component } = await render();
    component.special.set('RN');
    await accept(component, [P1, P2]);
    component.toggle(P1);
    component.toggle(P2);
    component.packagingQuantity.set('10');
    component.packagingType.set('USADO');

    component.generate();
    component.generate();

    const post = http.expectOne((r) => r.method === 'POST' && r.url === URL);
    expect(post.request.body).toEqual({
      fundCode: 'RP', specialType: 'RN', purchases: [{ module: 'DRY', id: 1 }, { module: 'HUSK', id: 7 }],
      packagingQuantity: 10, packagingType: 'USADO',
    });
    post.flush(REMITTANCE);
    await settle();
    expect(component.message()).toContain('SDXX-RP-0008');
    expect(component.found()?.number).toBe('SDXX-RP-0008');
    // Las facturas remesadas salen de pendientes: la lista se vuelve a pedir.
    http.expectOne((r) => r.url === `${URL}/pending`).flush([]);
    http.expectOne(`${URL}/9/files`).flush(FILES);
    await settle();
    expect(component.selected().size).toBe(0);
    expect(component.books().map((b) => b.sheet)).toEqual(['ParaOperaciones', 'ParaOpen', 'GeneraPlano', 'ParaAlmacafe']);
  });

  it('cannot generate without invoices or without packaging', async () => {
    const { component } = await render();
    component.special.set('RN');
    await accept(component, [P1]);
    component.packagingQuantity.set('10');
    component.packagingType.set('USADO');
    expect(component.canGenerate()).toBe(false);
    component.toggle(P1);
    expect(component.canGenerate()).toBe(true);
    component.packagingQuantity.set('0');
    expect(component.canGenerate()).toBe(false);
    component.packagingQuantity.set('5');
    component.packagingType.set('');
    expect(component.canGenerate()).toBe(false);
  });

  it('finds a remittance by number; only the Administrator can unmark it, after confirming', async () => {
    const { component } = await render();
    component.search('sdxx-rp-0008');
    await settle();
    http.expectOne((r) => r.url === URL && r.params.get('number') === 'sdxx-rp-0008').flush([REMITTANCE]);
    await settle();
    expect(component.found()?.number).toBe('SDXX-RP-0008');
    expect(component.canUnmark()).toBe(false);

    TestBed.inject(AuthService).roleName.set('ADMINISTRADOR');
    expect(component.canUnmark()).toBe(true);
    component.askUnmark();
    expect(component.confirmingUnmark()).toBe(true);
    component.unmark();
    http.expectOne(`${URL}/9/unmark`).flush({ ...REMITTANCE, status: 'DESMARCADA', purchases: 0 });
    await settle();
    expect(component.found()?.status).toBe('DESMARCADA');
    expect(component.message()).toContain('SDXX-RP-0008');
    expect(component.canUnmark()).toBe(false);
  });

  it('says so when no remittance has that number', async () => {
    const { component } = await render();
    component.search('NO-EXISTE');
    await settle();
    http.expectOne((r) => r.url === URL).flush([]);
    await settle();
    expect(component.message()).toBe(content.messages.notFound);
  });

  it('each file has its own button: the xlsx is built beforehand and the click downloads it at once', async () => {
    const { component } = await render();
    const saveFile = vi.fn();
    component.saveFile = saveFile;
    component.search('SDXX-RP-0008');
    await settle();
    http.expectOne((r) => r.url === URL).flush([REMITTANCE]);
    await settle();
    http.expectOne(`${URL}/9/files`).flush(FILES);
    await vi.waitFor(() => expect(component.filesReady()).toBe(true));

    // Sin esperar nada entre el clic y la descarga: Chrome solo deja pasar las que salen del clic.
    component.download(component.books()[3]);
    expect(saveFile).toHaveBeenCalledTimes(1);
    const [name, blob] = saveFile.mock.calls[0] as [string, Blob];
    expect(name).toBe('ParaAlmacafe_SDXX-RP-0008.xlsx');
    expect(new TextDecoder().decode(new Uint8Array(await blob.arrayBuffer()).slice(0, 2))).toBe('PK');
  });
});
