import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Subject, of } from 'rxjs';
import pdfMake from 'pdfmake/build/pdfmake';
import { describe, expect, it, vi } from 'vitest';

import { HuskFormComponent } from './husk-form';
import { ContentService } from '../../../core/services/content.service';
import { GrowerService } from '../../../core/services/grower.service';
import { HuskPurchaseService } from '../../../core/services/husk-purchase.service';
import { Grower } from '../../../core/models/grower.model';
import { PurchaseFormContent } from '../../../core/models/purchase-form-content.model';
import { HUSK } from '../../purchase-query/purchase-query.testing';

/**
 * Husk (Pasilla) usa un gate distinto al de Café Seco: buildRequest() no exige los campos REQUIRED
 * (solo agencyId/invoiceReservation/announcementInfo, que ya estan listos desde el constructor) - el
 * REQUIRED array solo gatea canPrint(). Igual comparte el mismo mecanismo de auto-lock en
 * lookupGrower() donde vivia el bug del celular bloqueado (ver dry-coffee-form.spec.ts).
 */

describe('HuskFormComponent - gates de captura', () => {
  const content: PurchaseFormContent & { messages: Record<string, string> } = {
    windowTitle: 'Compra Pasilla',
    theme: 'teal',
    topFields: [],
    identificationFields: [],
    messages: {
      acceptLabel: 'Aceptar',
      noAnnouncement: 'No hay anuncio',
      deceasedBlocked: 'No se le puede facturar a un fallecido.',
      idNumberNotFound: 'Este número de cédula no existe.',
      saveError: 'No se pudo registrar la compra. Verifique los datos.',
      savedNotice: 'Compra registrada.',
      invoiceError: 'No se pudo generar la factura',
      closeWarning: 'Hay datos sin guardar.',
      closeConfirm: 'Cerrar',
      closeCancel: 'Cancelar',
    },
  };

  const announcementInfo = {
    fundId: 1,
    productCode: '0110002000036',
    announcementNumber: 'SDBU-5',
    announcementDate: '2026-09-23',
    basePriceDryLoad: 1113500,
    pointPrice: 8908,
    costs: 692,
  };

  const growerWithPhone: Grower = {
    id: 1,
    idNumber: '210514',
    firstName: 'PAZ',
    secondName: 'CAROLINA',
    lastName: 'CASTELBLANCO',
    secondLastName: 'OSSA',
    address: 'CLL 14 N 37-20 CASA II',
    phone: '3117498703',
    growerType: 'C',
    active: true,
    deceased: false,
    withdrawn: false,
    transportCompany: null,
    vehiclePlate: null,
  };

  const growerWithoutPhone: Grower = { ...growerWithPhone, idNumber: '27197019', phone: '' };

  const calculation = {
    netKg: 1200,
    almondPercentage: 80,
    unitPrice: 56319,
    grossValue: 67582800,
    inventoryValue: 67582800,
    associateContribution: 0,
    cooperativeDiscount: 540662,
    withholding: 0,
    netToPay: 67042138,
  };

  let previewSpy: ReturnType<typeof vi.fn>;
  let createSpy: ReturnType<typeof vi.fn>;
  let component: HuskFormComponent;

  function setup(grower: Grower) {
    previewSpy = vi.fn(() => of(calculation));
    const fakeHuskService = {
      nextInvoiceNumber: () => of({ invoiceNumber: 100, prefix: 'SDBU', warning: null }),
      announcementInfo: () => of(announcementInfo),
      preview: previewSpy,
      create: (createSpy = vi.fn(() => of({}))),
    };
    const fakeGrowerService = { findByIdNumber: vi.fn(() => of(grower)) };

    localStorage.setItem('cafeoccidente.agencyId', '1');
    localStorage.setItem('cafeoccidente.agencyName', 'Buesaco');

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ContentService, useValue: { loadJson: () => of(content) } },
        { provide: HuskPurchaseService, useValue: fakeHuskService },
        { provide: GrowerService, useValue: fakeGrowerService },
      ],
    });
    component = TestBed.createComponent(HuskFormComponent).componentInstance;
  }

  function fillEverythingAfterGrower(): void {
    component.model['almondWeight'] = '200';
    component.onFieldCommitted('almondWeight');
    component.model['bags'] = '10';
    component.onFieldCommitted('bags');
    component.model['grossKg'] = '1250';
    component.onFieldCommitted('grossKg');
    component.model['tare'] = '50';
    component.onFieldCommitted('tare');
    component.model['shrinkageDiscount'] = '0';
    component.onFieldCommitted('shrinkageDiscount');
    component.model['otherDiscounts'] = '0';
    component.onFieldCommitted('otherDiscounts');
  }

  it('REGRESIÓN: a grower with a blank migrated phone does not get cellphone permanently locked', () => {
    setup(growerWithoutPhone);
    component.model['idPart1'] = growerWithoutPhone.idNumber;
    component.onFieldCommitted('idPart1');

    expect((component as any).locked.has('cellphone')).toBe(false);
    expect(component.model['cellphone']).toBe('');

    component.model['cellphone'] = '3001112233';
    component.onFieldCommitted('cellphone');
    expect((component as any).locked.has('cellphone')).toBe(true);
  });

  it('locks the migrated fields normally when the grower data is complete', () => {
    setup(growerWithPhone);
    component.model['idPart1'] = growerWithPhone.idNumber;
    component.onFieldCommitted('idPart1');

    expect((component as any).locked.has('cellphone')).toBe(true);
    expect((component as any).locked.has('firstName')).toBe(true);
    expect((component as any).locked.has('lastName')).toBe(true);
  });

  it('canPrint() stays false until every REQUIRED field is filled, otherDiscounts is confirmed and the payment balances', () => {
    setup(growerWithPhone);
    expect((component as any).canPrint()).toBe(false);

    component.model['idPart1'] = growerWithPhone.idNumber;
    component.onFieldCommitted('idPart1');
    expect((component as any).canPrint()).toBe(false);

    fillEverythingAfterGrower();
    // Ademas hace falta FORMAS DE PAGO cuadrado (lo emite el panel de pago).
    expect((component as any).canPrint()).toBe(false);
    component.payment.set({ cashAmount: 1, checkAmount: 0, transferAmount: 0, cardAmount: 0, checkNumber: null });
    expect((component as any).canPrint()).toBe(true);
  });

  it('recalculate() populates calc() once the full cascade is confirmed', () => {
    setup(growerWithPhone);
    component.model['idPart1'] = growerWithPhone.idNumber;
    component.onFieldCommitted('idPart1');
    fillEverythingAfterGrower();

    expect(previewSpy).toHaveBeenCalled();
    expect(component.calc()).toEqual(calculation);
  });

  it('blocks the whole form when the grower is deceased', () => {
    setup({ ...growerWithPhone, deceased: true });
    component.model['idPart1'] = growerWithPhone.idNumber;
    component.onFieldCommitted('idPart1');

    expect(component.blockedByDeceased()).toBe(true);
    expect(component.errorMessage()).toBe(content.messages['deceasedBlocked']);
    expect((component as any).canPrint()).toBe(false);
  });

  it('HU-15: two clicks on Imprimir create a single purchase, without sending the invoice number', () => {
    setup(growerWithPhone);
    component.model['idPart1'] = growerWithPhone.idNumber;
    component.onFieldCommitted('idPart1');
    fillEverythingAfterGrower();
    component.payment.set({ cashAmount: 1, checkAmount: 0, transferAmount: 0, cardAmount: 0, checkNumber: null });
    const pending = new Subject<never>();
    createSpy.mockReturnValue(pending);

    component.print();
    component.print();

    expect(createSpy).toHaveBeenCalledTimes(1);
    // El numero lo asigna el servidor al guardar; el encabezado solo lo muestra.
    expect(createSpy.mock.calls[0][0]).not.toHaveProperty('invoiceNumber');
    // 6b: precios, costos y datos del vendedor los pone el servidor (anuncio vigente y TERCEROS).
    for (const key of ['healthyUnitPrice', 'bonus', 'costs', 'pointPrice', 'firstName', 'lastName', 'growerType']) {
      expect(createSpy.mock.calls[0][0]).not.toHaveProperty(key);
    }
    expect(component.saving()).toBe(true);

    pending.error(new Error('fallo'));
    expect(component.saving()).toBe(false);
  });

  it('Imprimir saves, opens the invoice with the payment and leaves the form clean with the next invoice', async () => {
    setup(growerWithPhone);
    component.model['idPart1'] = growerWithPhone.idNumber;
    component.onFieldCommitted('idPart1');
    fillEverythingAfterGrower();
    const nextInvoice = vi.spyOn(TestBed.inject(HuskPurchaseService), 'nextInvoiceNumber');
    const open = vi.fn();
    const createPdf = vi.spyOn(pdfMake, 'createPdf').mockReturnValue({ open } as unknown as ReturnType<typeof pdfMake.createPdf>);
    component.payment.set({ cashAmount: 0, checkAmount: 0, transferAmount: 7777777, cardAmount: 0, checkNumber: null });
    createSpy.mockReturnValue(of(HUSK));

    component.print();

    expect(component.model['idPart1']).toBeUndefined();
    expect(component.payment()).toBeNull();
    expect(component.savedNoticeOpen()).toBe(true);
    expect(nextInvoice).toHaveBeenCalledTimes(1);
    await vi.waitFor(() => expect(open).toHaveBeenCalledTimes(1));
    const doc = JSON.stringify(createPdf.mock.lastCall![0]);
    expect(doc).toContain('SDBU - 27.872');
    expect(doc).toContain('7.777.777');
    createPdf.mockRestore();
  });

  it('says "No se pudo generar la factura" when the invoice PDF fails', async () => {
    setup(growerWithPhone);
    component.model['idPart1'] = growerWithPhone.idNumber;
    component.onFieldCommitted('idPart1');
    fillEverythingAfterGrower();
    const createPdf = vi.spyOn(pdfMake, 'createPdf').mockImplementation(() => {
      throw new Error('pdfmake');
    });
    component.payment.set({ cashAmount: 1, checkAmount: 0, transferAmount: 0, cardAmount: 0, checkNumber: null });
    createSpy.mockReturnValue(of(HUSK));

    component.print();

    await vi.waitFor(() => expect(component.errorMessage()).toBe(content.messages['invoiceError']));
    createPdf.mockRestore();
  });
});
