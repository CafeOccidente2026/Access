import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { API_BASE_URL } from '../../core/config/api.config';
import { AnnulmentCandidate } from '../../core/models/purchase-annulment.model';
import { ContentService } from '../../core/services/content.service';
import correction from '../../../../public/assets/data/purchase-correction.json';
import query from '../../../../public/assets/data/purchase-query.json';
import { DRY, HUSK, MIXED, detail } from '../purchase-query/purchase-query.testing';
import { PurchaseCorrectionComponent } from './purchase-correction';

const URL = `${API_BASE_URL}/purchases/correction`;
const QUERY = `${API_BASE_URL}/purchases/query`;
const FOUND: AnnulmentCandidate = {
  module: 'DRY', id: 1225, agencyId: 1, agencyName: 'Buesaco 1', prefix: 'SDBU', invoiceNumber: 27869,
  purchaseDate: '2026-10-29', fundCode: 'RP', idNumber: '990006003', firstName: 'PRUEBA', lastName: null,
  specialType: 'RN', netKg: 1000, netToPay: 12255579, paymentMethod: 'MIXTO', status: 'VALIDA', annulmentLocked: false,
  annulledAt: null,
};
const CALC = { netKg: 800, grossValue: 9933600, withholding: 49668, netToPay: 9804268 };

/** HU-05 (6D2): "Menu Habilitacion" corrige la compra con la misma factura (respuesta del area, 11-nov). */
describe('PurchaseCorrectionComponent (HU-05)', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ContentService, useValue: { loadJson: (name: string) => of(name === 'purchase-query' ? query : correction) } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  async function settle(): Promise<void> {
    await Promise.resolve();
    TestBed.tick();
  }

  /** Abre la primera compra: lista, detalle de Consultar Compras y la vista previa con sus mismos valores. */
  async function open(shown = detail(), found = FOUND) {
    const fixture = TestBed.createComponent(PurchaseCorrectionComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    component.go('FIRST');
    TestBed.tick();
    http.expectOne(`${URL}/navigate?direction=FIRST`).flush([found]);
    await settle();
    http.expectOne(`${QUERY}/${found.module}/${found.id}`).flush(shown);
    await settle();
    fixture.detectChanges();
    return { fixture, component, el: fixture.nativeElement as HTMLElement };
  }

  const preview = (module = 'DRY', id = 1225) => http.expectOne(`${URL}/${module}/${id}/preview`);
  const keys = (el: HTMLElement) =>
    [...el.querySelectorAll<HTMLInputElement>('[data-correction] [data-field-key]')].map((i) => i.dataset['fieldKey']);

  it('shows the purchase read only and lets edit only the fields the area allowed, plus the payment', async () => {
    const { component, el } = await open();
    const request = preview();
    expect(request.request.method).toBe('POST');
    // La primera vista previa va con los valores guardados; fondo, cedula y especial los fija el servidor.
    expect(request.request.body).toMatchObject({ idNumber: DRY.idNumber, specialType: 'RN', grossKg: 1000, bagsCount: 5 });
    request.flush({ ...CALC, netKg: 1000, netToPay: DRY.netToPay });
    await settle();

    expect(component.view()?.paymentLocked).toBe(true);
    expect(component.view()?.reprintButtonLabel).toBeFalsy();
    expect(keys(el)).toEqual([...correction.fields.DRY.map((f) => f.key), ...correction.paymentFields.map((f) => f.key)]);
    expect(el.querySelector<HTMLInputElement>('[data-correction] [data-field-key="grossKg"]')?.value).toBe('1000');
    expect(el.querySelector<HTMLInputElement>('[data-correction] [data-field-key="cardAmount"]')?.value).toBe(String(MIXED.cardAmount));
  });

  it('recalculates on the server when a weight changes and puts the difference in cash', async () => {
    const { component } = await open();
    preview().flush({ ...CALC, netKg: 1000, netToPay: DRY.netToPay });
    await settle();

    component.setValue('grossKg', '800');
    component.recalculate();
    await settle();
    preview().flush(CALC);
    await settle();

    expect(component.after()?.netToPay).toBe(CALC.netToPay);
    const others = MIXED.checkAmount + MIXED.transferAmount + MIXED.cardAmount;
    expect(component.payment()['cashAmount']).toBe(String(CALC.netToPay - others));
    expect(component.balanced()).toBe(true);
  });

  it('saves once even with two clicks, with the same invoice, and shows the purchase as corrected', async () => {
    const { component, fixture, el } = await open();
    preview().flush({ ...CALC, netKg: 1000, netToPay: DRY.netToPay });
    await settle();
    fixture.detectChanges();

    const save = el.querySelector<HTMLButtonElement>('[data-testid="save"]')!;
    save.click();
    save.click();
    const put = http.expectOne(`${URL}/DRY/1225`);
    expect(put.request.method).toBe('PUT');
    expect(put.request.body.payment).toEqual({ ...MIXED });
    expect(component.saving()).toBe(true);
    put.flush({ ...DRY });
    await settle();

    expect(component.message()).toBe(correction.messages.saved.replace('{invoice}', '27869'));
    http.expectOne(`${QUERY}/DRY/1225`).flush(detail({ corrected: true }));
    await settle();
    preview().flush({ ...CALC, netKg: 1000, netToPay: DRY.netToPay });
    await settle();
    expect(component.view()?.statusField?.value).toBe(`VALIDA - ${query.correctedMark}`);
  });

  it('does not save while the payment does not add up to the net', async () => {
    const { component, fixture, el } = await open();
    preview().flush({ ...CALC, netKg: 1000, netToPay: DRY.netToPay });
    await settle();

    component.setPayment('cardAmount', '1');
    fixture.detectChanges();

    expect(component.balanced()).toBe(false);
    expect(el.querySelector<HTMLButtonElement>('[data-testid="save"]')?.disabled).toBe(true);
    expect(el.textContent).toContain(correction.messages.unbalanced);
  });

  it('when the purchase cannot be corrected it says why and nothing can be edited', async () => {
    const { fixture, el } = await open();
    preview().flush({ message: 'La compra está en la remesa SDBU-RP-0007: primero desmárquela' }, { status: 400, statusText: 'Bad Request' });
    await settle();
    fixture.detectChanges();

    expect(el.textContent).toContain('SDBU-RP-0007');
    expect(el.querySelector<HTMLFieldSetElement>('[data-correction] fieldset')?.disabled).toBe(true);
    expect(el.querySelector<HTMLButtonElement>('[data-testid="save"]')?.disabled).toBe(true);
  });

  it('an annulled purchase is shown but not corrected', async () => {
    const { el, fixture } = await open(detail({ status: 'ANULADA' }), { ...FOUND, status: 'ANULADA' });
    fixture.detectChanges();

    http.expectNone(`${URL}/DRY/1225/preview`);
    expect(el.textContent).toContain(correction.messages.annulled);
    expect(el.querySelector('[data-correction]')).toBeNull();
  });

  it('says why the save failed and lets try again', async () => {
    const { component, fixture, el } = await open();
    preview().flush({ ...CALC, netKg: 1000, netToPay: DRY.netToPay });
    await settle();
    fixture.detectChanges();

    component.save();
    http.expectOne(`${URL}/DRY/1225`).flush({ message: 'FORMA DE PAGO DESCUADRADA, REVISE' }, { status: 400, statusText: 'Bad Request' });
    await settle();
    fixture.detectChanges();

    expect(component.message()).toBe('FORMA DE PAGO DESCUADRADA, REVISE');
    expect(component.saving()).toBe(false);
    expect(el.querySelector<HTMLButtonElement>('[data-testid="save"]')?.disabled).toBe(false);
  });

  it('a purchase brought from Access without payment split starts all in cash', async () => {
    const { component } = await open(detail({ payment: null }));
    expect(component.payment()).toMatchObject({ cashAmount: String(DRY.netToPay), cardAmount: '0', checkNumber: '' });
    preview().flush({ ...CALC, netKg: 1000, netToPay: DRY.netToPay });
  });

  it('searches by invoice, says so when it does not exist and goes back to Acceso Principal', async () => {
    const fixture = TestBed.createComponent(PurchaseCorrectionComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);

    component.search(new Event('submit'), 'SDBU 99');
    TestBed.tick();
    http.expectOne(`${URL}?invoiceNumber=99`).flush([]);
    await settle();
    expect(component.message()).toBe(correction.messages.notFound);
    component.search(new Event('submit'), '');
    http.expectNone(`${URL}?invoiceNumber=0`);

    component.back();
    expect(navigate).toHaveBeenCalledWith(correction.backRoute);
  });

  it('when one invoice is in two modules, lets choose which one to correct', async () => {
    const other = { ...FOUND, module: 'HUSK' as const, id: 24 };
    const fixture = TestBed.createComponent(PurchaseCorrectionComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    component.search(new Event('submit'), '27869');
    TestBed.tick();
    http.expectOne(`${URL}?invoiceNumber=27869`).flush([FOUND, other]);
    await settle();
    http.expectOne(`${QUERY}/DRY/1225`).flush(detail());
    await settle();

    component.choose(other);
    await settle();
    http.expectOne(`${QUERY}/HUSK/24`).flush(detail({ module: 'HUSK', purchase: HUSK }));
    await settle();
    expect(component.fields().map((f) => f.key)).toContain('almondWeight');
    http.match(() => true);
  });

  it('corrects husk with its own fields', async () => {
    const { el } = await open(detail({ module: 'HUSK', purchase: HUSK }), { ...FOUND, module: 'HUSK', id: 24 });
    preview('HUSK', 24).flush({ ...CALC, netKg: 1000, netToPay: HUSK.netToPay });
    await settle();

    expect(keys(el)).toEqual([...correction.fields.HUSK.map((f) => f.key), ...correction.paymentFields.map((f) => f.key)]);
  });

  it('the last field submits with Enter: the save button closes the keyboard flow', async () => {
    const { el, fixture } = await open();
    preview().flush({ ...CALC, netKg: 1000, netToPay: DRY.netToPay });
    await settle();
    fixture.detectChanges();

    expect(el.querySelector('[data-correction]')?.classList.contains('form-flow')).toBe(true);
    expect(el.querySelector('[data-testid="save"]')?.hasAttribute('data-flow-submit')).toBe(true);
  });
});
