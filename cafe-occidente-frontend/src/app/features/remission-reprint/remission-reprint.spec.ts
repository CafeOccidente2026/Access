import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import pdfMake from 'pdfmake/build/pdfmake';
import { describe, expect, it, vi } from 'vitest';

import { InventoryService } from '../../core/services/inventory.service';
import { RemissionReprintComponent } from './remission-reprint';

describe('RemissionReprintComponent', () => {
  it('prueba del 11-nov (punto 4): a failed search shows the reason of the server', () => {
    TestBed.configureTestingModule({
      providers: [{
        provide: InventoryService,
        useValue: { findRemissionByNumber: () => throwError(() => ({ error: { message: 'Falta el dato number' } })) },
      }],
    });
    const component = TestBed.createComponent(RemissionReprintComponent).componentInstance;
    component.remissionNumber.set('SDBU-RP-0001');

    component.search();

    expect(component.message()).toBe('Falta el dato number');
  });

  it('says so when the PDF cannot be built, instead of leaving the promise unhandled', async () => {
    const remission = { id: 1, displayNumber: 'SDBU-RP-0001', lines: [] };
    TestBed.configureTestingModule({
      providers: [{ provide: InventoryService, useValue: { findRemissionByNumber: () => of([remission]) } }],
    });
    const component = TestBed.createComponent(RemissionReprintComponent).componentInstance;
    const render = vi.spyOn(pdfMake, 'createPdf').mockImplementation(() => {
      throw new Error('pdfmake');
    });
    component.remissionNumber.set('SDBU-RP-0001');

    component.search();

    await vi.waitFor(() => expect(component.message()).toBe('No se pudo generar la remisión'));
    render.mockRestore();
  });
});
