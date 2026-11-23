import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';
import pdfMake from 'pdfmake/build/pdfmake';
import { describe, expect, it, vi } from 'vitest';

import { RemissionResponse } from '../../core/models/inventory.model';
import { AuthService } from '../../core/services/auth.service';
import { ContentService } from '../../core/services/content.service';
import { InventoryService } from '../../core/services/inventory.service';
import content from '../../../../public/assets/data/remission-reprint.json';
import { RemissionReprintComponent } from './remission-reprint';

const REMISSION = { id: 1, displayNumber: 'SDBU-125', status: 'VIGENTE', remittanceId: 9, lines: [] } as unknown as RemissionResponse;

describe('RemissionReprintComponent', () => {
  function setup(inventory: Partial<Record<keyof InventoryService, unknown>>, allowed = true) {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: InventoryService, useValue: inventory },
        { provide: ContentService, useValue: { loadJson: () => of(content) } },
        { provide: AuthService, useValue: { canAccess: () => allowed } },
      ],
    });
    const component = TestBed.createComponent(RemissionReprintComponent).componentInstance;
    component.remissionNumber.set('SDBU-125');
    return component;
  }

  it('prueba del 11-nov (punto 4): a failed search shows the reason of the server', () => {
    const component = setup({ findRemissionByNumber: () => throwError(() => ({ error: { message: 'Falta el dato number' } })) });

    component.search();

    expect(component.message()).toBe('Falta el dato number');
  });

  it('asks for the number and says so when no remission has it', () => {
    const component = setup({ findRemissionByNumber: () => of([]) });
    component.remissionNumber.set(' ');
    component.search();
    expect(component.message()).toBe(content.messages.missingNumber);

    component.remissionNumber.set('NO-EXISTE');
    component.search();
    expect(component.message()).toBe(content.messages.notFound);
    expect(component.found()).toBeNull();
  });

  it('says so when the PDF cannot be built, instead of leaving the promise unhandled', async () => {
    const component = setup({ findRemissionByNumber: () => of([REMISSION]) });
    const render = vi.spyOn(pdfMake, 'createPdf').mockImplementation(() => {
      throw new Error('pdfmake');
    });

    component.search();

    await vi.waitFor(() => expect(component.message()).toBe(content.messages.pdfError));
    expect(component.found()?.remittanceId).toBe(9);
    render.mockRestore();
  });

  it('Anular Remisión: only with permission and while in force; two clicks annul it once', () => {
    const pending = new Subject<RemissionResponse>();
    const annulRemission = vi.fn(() => pending);
    const component = setup({ findRemissionByNumber: () => of([REMISSION]), annulRemission });
    component.search();
    expect(component.canAnnul()).toBe(true);
    expect(component.confirmText(content)).toContain('SDBU-125');

    component.annul();
    component.annul();
    expect(annulRemission).toHaveBeenCalledTimes(1);
    expect(annulRemission).toHaveBeenCalledWith(1);
    pending.next({ ...REMISSION, status: 'ANULADA' });
    pending.complete();

    expect(component.message()).toBe('Se anuló la remisión SDBU-125.');
    expect(component.canAnnul()).toBe(false);
  });

  it('without the permission there is no Anular Remisión, and a refusal of the server is shown', () => {
    const denied = setup({ findRemissionByNumber: () => of([REMISSION]) }, false);
    denied.search();
    expect(denied.canAnnul()).toBe(false);

    TestBed.resetTestingModule();
    const component = setup({
      findRemissionByNumber: () => of([REMISSION]),
      annulRemission: () => throwError(() => ({ error: { message: 'La remisión SDBU-125 ya está anulada' } })),
    });
    component.search();
    component.annul();
    expect(component.message()).toBe('La remisión SDBU-125 ya está anulada');
  });
});
