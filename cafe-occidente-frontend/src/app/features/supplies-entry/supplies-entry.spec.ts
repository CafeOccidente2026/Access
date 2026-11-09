import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it } from 'vitest';

import { API_BASE_URL } from '../../core/config/api.config';
import { SuppliesEntryResponse } from '../../core/models/supplies.model';
import { ContentService } from '../../core/services/content.service';
import content from '../../../../public/assets/data/supplies-entry.json';
import { SuppliesEntryComponent } from './supplies-entry';

const URL = `${API_BASE_URL}/supplies`;

const saved = (transactionId: string): SuppliesEntryResponse => ({
  id: 1, transactionId, agencyName: 'El Tambo', entryDate: '2026-11-05', idNumber: '87304051', firstNames: 'ANA',
  lastNames: 'PRUEBA',
});

describe('SuppliesEntryComponent (5c, HU-36 a HU-48)', () => {
  let http: HttpTestingController;

  async function render(screen: string) {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ContentService, useValue: { loadJson: () => of(content) } },
        { provide: ActivatedRoute, useValue: { snapshot: { data: { screen } } } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(SuppliesEntryComponent);
    fixture.detectChanges();
    await settle();
    return { fixture, component: fixture.componentInstance };
  }

  async function settle(): Promise<void> {
    await Promise.resolve();
    TestBed.tick();
    await Promise.resolve();
  }

  /** Responde el Id que pide la pantalla con screen y value. */
  async function answerNextId(screen: string, value: string | null, transactionId: string) {
    const request = http.expectOne((r) => r.url === `${URL}/next-id` && r.params.get('screen') === screen
      && r.params.get('value') === value);
    request.flush({ transactionId });
    await settle();
  }

  it('shows the Id the server will take and refreshes it when the fund changes', async () => {
    const { component } = await render('cash');
    // Sin fondo elegido todavia no pide Id.
    http.expectNone(`${URL}/next-id`);

    component.set('fundCode', 'RP');
    await settle();
    await answerNextId('cash', 'RP', 'SDTA2001');
    expect(component.values()['transactionId']).toBe('SDTA2001');

    component.set('fundCode', 'LF');
    await settle();
    await answerNextId('cash', 'LF', 'SDTA1001');
    expect(component.values()['transactionId']).toBe('SDTA1001');
  });

  it.each(['cash', 'adjustment', 'pettyCashExpense', 'supply', 'issuedCheck', 'packaging'])(
    '%s: typing the cédula shows the third party name beside it (prueba del 9-nov, punto 2)',
    async (screen) => {
      const { fixture, component } = await render(screen);
      const box = (fixture.nativeElement as HTMLElement).querySelector<HTMLInputElement>('[data-field-key="idNumber"]')!;
      box.value = '1082656111';
      box.dispatchEvent(new Event('input'));
      box.dispatchEvent(new Event('blur'));
      http.expectOne(`${API_BASE_URL}/growers/1082656111`).flush({
        idNumber: '1082656111', firstName: 'ANA', secondName: 'MARIA', lastName: 'ERAZO', secondLastName: 'PAZ',
      });
      await settle();

      expect(component.growerNames()).toEqual({ first: 'ANA MARIA', last: 'ERAZO PAZ' });
    },
  );

  it('Caja Menor asks its Id without a fund and does not ask for a cédula', async () => {
    const { component } = await render('pettyCash');
    await answerNextId('pettyCash', null, 'SDTA7ING001');
    component.set('amount', '50000');

    component.save();
    const request = http.expectOne(`${URL}/petty-cash`);
    expect(request.request.body).toMatchObject({ amount: 50000, fundCode: 'RP' });
    expect(request.request.body).not.toHaveProperty('transactionId');
  });

  it('two clicks on Guardar send one request and the saved Id is shown', async () => {
    const { component } = await render('issuedCheck');
    component.set('fundCode', 'RP');
    await settle();
    await answerNextId('issuedCheck', 'RP', 'SDTA3CHQ001');
    component.set('idNumber', '87304051');
    component.set('amount', '250000');
    component.set('checkNumber', '0548');

    component.save();
    component.save();
    const requests = http.match(`${URL}/issued-checks`);
    expect(requests).toHaveLength(1);
    expect(requests[0].request.body).toMatchObject({ checkNumber: '0548', idNumber: '87304051' });
    requests[0].flush(saved('SDTA3CHQ001'));
    await settle();

    expect(component.message()).toBe(content.messages.saved.replace('{id}', 'SDTA3CHQ001'));
    // El formulario queda limpio; al elegir otra vez el fondo pide el Id siguiente (no reutiliza el anterior).
    expect(component.values()['fundCode']).toBe('');
    component.set('fundCode', 'RP');
    await settle();
    await answerNextId('issuedCheck', 'RP', 'SDTA3CHQ002');
    expect(component.values()['transactionId']).toBe('SDTA3CHQ002');
  });

  it('a check that is not 4 digits is stopped before calling the server', async () => {
    const { component } = await render('issuedCheck');
    component.set('fundCode', 'RP');
    await settle();
    await answerNextId('issuedCheck', 'RP', 'SDTA3CHQ001');
    component.set('idNumber', '87304051');
    component.set('amount', '250000');
    component.set('checkNumber', '548');

    component.save();

    http.expectNone(`${URL}/issued-checks`);
    expect(component.error()).toBe(true);
    expect(component.message()).toBe(content.messages.checkDigits);
  });

  it('Préstamo Empaques keeps the saved Id on screen until Nuevo Préstamo, then asks again', async () => {
    const { component } = await render('packagingLoan');
    component.set('packagingType', 'USADO');
    await settle();
    await answerNextId('packagingLoan', 'USADO', 'SDTA5PRE001');
    component.set('idNumber', '87304051');
    component.set('quantity', '12');

    component.save();
    http.expectOne(`${URL}/packaging-loans`).flush(saved('SDTA5PRE001'));
    await settle();
    // Como Access: los datos quedan a la vista para imprimir el comprobante.
    expect(component.saved()?.transactionId).toBe('SDTA5PRE001');
    expect(component.values()['transactionId']).toBe('SDTA5PRE001');
    expect(component.values()['quantity']).toBe('12');
    // El Id siguiente ya se pide, pero mientras el prestamo guardado esta a la vista se muestra el suyo.
    await answerNextId('packagingLoan', 'USADO', 'SDTA5PRE002');
    expect(component.values()['transactionId']).toBe('SDTA5PRE001');

    component.reset();
    await settle();
    expect(component.values()['transactionId']).toBe('');
    component.set('packagingType', 'USADO');
    await settle();
    await answerNextId('packagingLoan', 'USADO', 'SDTA5PRE002');
    expect(component.saved()).toBeNull();
    expect(component.values()['transactionId']).toBe('SDTA5PRE002');
  });

  it('shows the server message when the cédula is not in TERCEROS', async () => {
    const { component } = await render('packaging');
    component.set('packagingType', 'USADO');
    await settle();
    await answerNextId('packaging', 'USADO', 'SDTA5SUM001');
    component.set('idNumber', '990005999');
    component.set('quantity', '10');

    component.save();
    http.expectOne(`${URL}/packaging`).flush(
      { message: 'La cédula 990005999 no está registrada en TERCEROS' }, { status: 400, statusText: 'Bad Request' });
    await settle();

    expect(component.error()).toBe(true);
    expect(component.message()).toBe('La cédula 990005999 no está registrada en TERCEROS');
    expect(component.saving()).toBe(false);
  });
});

describe('supplies-entry.json: valores con puntos de miles al escribir (prueba del 9-nov)', () => {
  it('todo campo de valor de Caja, Suministros, Caja Menor y Ajustes los muestra', () => {
    const currency = JSON.stringify(content).match(/\{[^{}]*"type":"currency"[^{}]*\}/g) ?? [];
    expect(currency.length).toBeGreaterThan(0);
    for (const field of currency) {
      expect(field).toContain('"liveThousands":true');
    }
  });
});
