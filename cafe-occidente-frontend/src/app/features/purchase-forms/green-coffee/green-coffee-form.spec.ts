import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Subject, of } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';

import { GreenCoffeeFormComponent } from './green-coffee-form';
import { ContentService } from '../../../core/services/content.service';
import { GreenCoffeePurchaseService } from '../../../core/services/green-coffee-purchase.service';
import { GrowerService } from '../../../core/services/grower.service';
import { Grower } from '../../../core/models/grower.model';
import { PurchaseFormContent } from '../../../core/models/purchase-form-content.model';

describe('GreenCoffeeFormComponent - guardado', () => {
  const content: PurchaseFormContent & { messages: Record<string, string> } = {
    windowTitle: 'Compra Café Verde',
    theme: 'teal',
    topFields: [],
    identificationFields: [],
    weightFields: [
      { key: 'totalStoredWeight', label: 'Peso Tot Alm', type: 'count' },
      { key: 'factor', label: 'Factor', type: 'number' },
    ],
    messages: {
      sampleMissing: 'Faltan los valores de café verde en el Registro de Control.',
      acceptLabel: 'Aceptar',
      noAnnouncement: 'No hay anuncio',
      deceasedBlocked: 'No se le puede facturar a un fallecido.',
      idNumberNotFound: 'Este número de cédula no existe.',
      saveError: 'No se pudo registrar la compra. Verifique los datos.',
      savedNotice: 'Compra registrada.',
      closeWarning: 'Hay datos sin guardar.',
      closeConfirm: 'Cerrar',
      closeCancel: 'Cancelar',
    },
  };

  const grower: Grower = {
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

  let createSpy: ReturnType<typeof vi.fn>;
  let component: GreenCoffeeFormComponent;

  const sample = {
    totalStoredWeight: 140, wastePercentage: 44, huskWeight: 12.5, huskPercentage: 5, healthyStoredWeight: 186.18, factor: 94,
  };

  function setup(sampleReady = true): void {
    const fakeGreenService = {
      nextInvoiceNumber: () => of({ invoiceNumber: 100, prefix: 'SDBU', warning: null }),
      announcementInfo: () =>
        of({
          fundId: 1,
          productCode: '0110002000010',
          announcementNumber: 'SDBU-5',
          announcementDate: '2026-09-23',
          basePriceLoad: 1113500,
          defectiveUnitPrice: 6000,
          healthyUnitPrice: 12000,
          bonus: 0,
          costs: 692,
          sample: sampleReady ? sample : null,
          sampleReady,
        }),
      preview: vi.fn(() => of({ netKg: 1200, withholding: 0, netToPay: 1000 })),
      create: (createSpy = vi.fn(() => of({}))),
    };

    localStorage.setItem('cafeoccidente.agencyId', '1');
    localStorage.setItem('cafeoccidente.agencyName', 'Buesaco');

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ContentService, useValue: { loadJson: () => of(content) } },
        { provide: GreenCoffeePurchaseService, useValue: fakeGreenService },
        { provide: GrowerService, useValue: { findByIdNumber: vi.fn(() => of(grower)) } },
      ],
    });
    component = TestBed.createComponent(GreenCoffeeFormComponent).componentInstance;
  }

  function fillEverything(): void {
    const values: [string, string][] = [
      ['idPart1', grower.idNumber], ['bags', '10'], ['grossKg', '1250'], ['tare', '50'], ['compKgPrice', '12000'],
      ['penalty', '0'], ['shrinkageDiscount', '0'], ['otherDiscounts', '0'],
    ];
    for (const [key, value] of values) {
      component.model[key] = value;
      component.onFieldCommitted(key);
    }
    component.payment.set({ cashAmount: 1, checkAmount: 0, transferAmount: 0, cardAmount: 0, checkNumber: null });
  }

  it('HU-15: two clicks on Imprimir create a single purchase, without sending the invoice number', () => {
    setup();
    fillEverything();
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

  it('HU-23 CA-2: shows the fixed sample of the control record as read-only', () => {
    setup();

    const fields = component.viewContent()?.weightFields ?? [];
    expect(fields.find((f) => f.key === 'totalStoredWeight')).toMatchObject({ readonly: true, value: 140 });
    expect(fields.find((f) => f.key === 'factor')).toMatchObject({ readonly: true, value: 94 });
  });

  it('Q23: without the green weights it warns on open and nothing can be typed or saved', () => {
    setup(false);
    fillEverything();
    component.print();

    expect(component.errorMessage()).toBe(content.messages['sampleMissing']);
    expect((component as any).locked.has('bags')).toBe(false);
    expect((component as any).locked.has('compKgPrice')).toBe(false);
    expect(createSpy).not.toHaveBeenCalled();
    // Sin muestra, los 6 campos de la muestra quedan vacios y de solo lectura (antes salian fijos del JSON).
    const fields = component.viewContent()?.weightFields ?? [];
    expect(fields.find((f) => f.key === 'totalStoredWeight')).toMatchObject({ readonly: true, value: '' });
    expect(fields.find((f) => f.key === 'factor')).toMatchObject({ readonly: true, value: '' });
  });
});
