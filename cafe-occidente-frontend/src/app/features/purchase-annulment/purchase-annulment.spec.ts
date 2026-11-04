import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it } from 'vitest';

import { API_BASE_URL } from '../../core/config/api.config';
import { AnnulmentCandidate } from '../../core/models/purchase-annulment.model';
import { ContentService } from '../../core/services/content.service';
import content from '../../../../public/assets/data/purchase-annulment.json';
import menu from '../../../../public/assets/data/purchases-menu.json';
import { PurchaseAnnulmentComponent, PurchaseAnnulmentContent, fieldValue } from './purchase-annulment';

const data = content as PurchaseAnnulmentContent;
const URL = `${API_BASE_URL}/purchases/annulment`;
const candidate = {
  module: 'DRY', id: 1, agencyId: 4, agencyName: 'El Tambo', prefix: 'SDTA', invoiceNumber: 43388,
  purchaseDate: '2026-06-03', fundCode: 'RP', idNumber: '79374104', firstName: 'JESUS', lastName: null,
  specialType: 'NESPRESSO - FTUSA', netKg: 1200.5, netToPay: 9267436, paymentMethod: 'MIXTO', status: 'VALIDA',
  annulmentLocked: false, annulledAt: null,
} as AnnulmentCandidate;
const next = { ...candidate, id: 2, invoiceNumber: 43389 } as AnnulmentCandidate;
const annulled = { ...candidate, netKg: 0, netToPay: 0, status: 'ANULADA', annulledAt: '2026-10-28T10:00:00Z' } as AnnulmentCandidate;

describe('purchase annulment', () => {
  it('the Compras menu button opens the screen', () => {
    expect(menu.options.find((o) => o.label === 'Anular Documento')?.route).toBe('/compras/anular');
  });

  it('shows the Access card values', () => {
    const v = (key: string) => fieldValue(candidate, key, data.fields.find((f) => f.key === key)?.type, data);
    expect(v('module')).toBe('Café Seco');
    expect(v('invoice')).toBe('SDTA 43388');
    expect(v('name')).toBe('JESUS');
    expect(v('purchaseDate')).toBe('03/06/2026');
    expect(v('netKg')).toBe('1.200,50');
    expect(v('netToPay')).toBe('9.267.436');
  });

  it('RN-23.3: asks with the RF question and Aceptar / Cancelar; a purchase closed in Access cannot be annulled', () => {
    expect(data.confirmMessage).toBe('¿Está seguro de querer anular este documento?');
    expect([data.yesLabel, data.noLabel]).toEqual(['Aceptar', 'Cancelar']);
    expect(data.lockedMessage).toBe('IMPOSIBLE ANULAR: ES UNA COMPRA CERRADA EN EL SISTEMA ANTERIOR.');
  });
});

describe('PurchaseAnnulmentComponent (HU-25)', () => {
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
    const fixture = TestBed.createComponent(PurchaseAnnulmentComponent);
    fixture.detectChanges();
    return { fixture, component: fixture.componentInstance, el: fixture.nativeElement as HTMLElement };
  }

  /** Muestra la ficha de la primera compra con |◄. */
  function showFirst(component: PurchaseAnnulmentComponent, purchase = candidate): void {
    component.go('FIRST');
    http.expectOne(`${URL}/navigate?direction=FIRST`).flush([purchase]);
  }

  function askToAnnul(fixture: ReturnType<typeof render>['fixture'], el: HTMLElement): void {
    fixture.componentInstance.startAnnul();
    fixture.detectChanges();
    expect(el.textContent).toContain(data.confirmMessage);
  }

  it('CA-1: walks the purchases one by one from the one on screen, and stays there at the end', () => {
    const { fixture, component, el } = render();
    expect(Array.from(el.querySelectorAll('[data-testid="navigation"] button')).map((b) => b.textContent?.trim()))
      .toEqual(['|◄', '◄', '►', '►|']);

    showFirst(component);
    component.go('NEXT');
    http.expectOne(`${URL}/navigate?direction=NEXT&from=43388`).flush([next]);
    expect(component.selected()?.invoiceNumber).toBe(43389);

    component.go('NEXT');
    http.expectOne(`${URL}/navigate?direction=NEXT&from=43389`).flush([]);
    fixture.detectChanges();
    expect(component.selected()?.invoiceNumber).toBe(43389);
    expect(el.textContent).toContain(data.messages.noMore);
  });

  it('CA-1: finds the purchase by invoice number', () => {
    const { component } = render();
    component.search(new Event('submit'), 'SDTA 43388');
    http.expectOne(`${URL}?invoiceNumber=43388`).flush([candidate]);
    expect(component.selected()).toEqual(candidate);
  });

  it('CA-2: Aceptar annuls it and the card shows ANULADA with its values in zero', () => {
    const { fixture, component, el } = render();
    showFirst(component);
    askToAnnul(fixture, el);

    (el.querySelector('[data-testid="accept"]') as HTMLButtonElement).click();
    http.expectOne({ method: 'POST', url: `${URL}/DRY/1` }).flush(annulled);
    fixture.detectChanges();

    expect(component.selected()?.status).toBe('ANULADA');
    expect(component.prompt()).toBeNull();
    expect(el.textContent).toContain('Documento soporte 43388 anulado');
  });

  it('CA-4: Cancelar leaves the purchase as it was', () => {
    const { fixture, component, el } = render();
    showFirst(component);
    askToAnnul(fixture, el);

    (el.querySelector('[data-testid="cancel"]') as HTMLButtonElement).click();
    fixture.detectChanges();

    http.expectNone({ method: 'POST' });
    expect(component.prompt()).toBeNull();
    expect(component.selected()?.status).toBe('VALIDA');
  });

  it('a purchase closed in Access only shows the message, without asking the server', () => {
    const { fixture, component, el } = render();
    showFirst(component, { ...candidate, annulmentLocked: true });
    component.startAnnul();
    fixture.detectChanges();

    expect(el.textContent).toContain(data.lockedMessage);
    expect(el.querySelector('[data-testid="accept"]')).toBeNull();
    http.expectNone({ method: 'POST' });
  });

  it('shows the reason the server gives when it refuses to annul', () => {
    const { fixture, component, el } = render();
    showFirst(component);
    askToAnnul(fixture, el);
    component.confirm();
    http.expectOne(`${URL}/DRY/1`).flush({ message: 'La compra tiene una cuenta por cobrar ya saldada' },
      { status: 422, statusText: 'Unprocessable Entity' });
    fixture.detectChanges();

    expect(el.textContent).toContain('La compra tiene una cuenta por cobrar ya saldada');
    expect(component.saving()).toBe(false);
  });

  it('double submit: two clicks on Aceptar send one request and Aceptar is disabled meanwhile', () => {
    const { fixture, component, el } = render();
    showFirst(component);
    askToAnnul(fixture, el);
    const accept = el.querySelector('[data-testid="accept"]') as HTMLButtonElement;

    component.confirm();
    component.confirm();
    fixture.detectChanges();

    expect(accept.disabled).toBe(true);
    http.expectOne({ method: 'POST', url: `${URL}/DRY/1` }).flush(annulled);
    expect(component.saving()).toBe(false);
  });
});
