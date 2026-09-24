import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';

import { OtherCoffeeFormComponent } from './other-coffee-form';
import { ContentService } from '../../../core/services/content.service';
import { GrowerService } from '../../../core/services/grower.service';
import { OtherCoffeePurchaseService } from '../../../core/services/other-coffee-purchase.service';
import { Grower } from '../../../core/models/grower.model';
import { PurchaseFormContent } from '../../../core/models/purchase-form-content.model';

/**
 * Mismo gate REQUIRED/hasRequiredCascadeInputs que Café Seco (dry-coffee-form.spec.ts), y misma
 * regresión del celular bloqueado - Cafés Otros nació hoy con el fix ya aplicado desde el vamos,
 * este test lo deja probado igual que en los módulos donde se encontró el bug original.
 */
describe('OtherCoffeeFormComponent - gates de captura', () => {
  const content: PurchaseFormContent & { messages: Record<string, string> } = {
    windowTitle: 'COMPRASESP',
    theme: 'green',
    topFields: [],
    identificationFields: [],
    messages: {
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
    basePriceLoad: 1113500,
    netKg: 1200,
    wastePercentage: 12,
    defectivePercentage: 8,
    healthyPercentage: 90.9,
    unitPrice: 7218,
    grossValue: 8661600,
    inventoryValue: 8661600,
    associateContribution: 0,
    cooperativeDiscount: 69293,
    withholding: 0,
    netToPay: 8522146,
  };

  let previewSpy: ReturnType<typeof vi.fn>;
  let component: OtherCoffeeFormComponent;

  function setup(grower: Grower) {
    previewSpy = vi.fn(() => of(calculation));
    const fakeService = {
      funds: () => of([{ id: 1, code: 'RP', name: 'Resolución Propia' }]),
      nextInvoiceNumber: () => of({ invoiceNumber: 27872, prefix: 'SDBU', warning: null }),
      specialInfo: () =>
        of({
          productCode: '0110002000002',
          announcementNumber: 'SDBU-32',
          announcementDate: '2026-09-23',
          basePriceLoad: 1113500,
          defectiveUnitPrice: 0,
          healthyUnitPrice: 8908,
          bonus: 40,
          costs: 692,
        }),
      qualityPercentages: () => of({ wastePercentage: 12, defectivePercentage: 8, healthyPercentage: 90.9 }),
      preview: previewSpy,
      create: vi.fn(() => of({})),
    };
    const fakeGrowerService = { findByIdNumber: vi.fn(() => of(grower)), findProgram: () => of(null) };

    localStorage.setItem('cafeoccidente.agencyId', '1');
    localStorage.setItem('cafeoccidente.agencyName', 'Buesaco');

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ContentService, useValue: { loadJson: () => of(content) } },
        { provide: OtherCoffeePurchaseService, useValue: fakeService },
        { provide: GrowerService, useValue: fakeGrowerService },
      ],
    });
    component = TestBed.createComponent(OtherCoffeeFormComponent).componentInstance;
  }

  function fillCommon(): void {
    component.model['fund'] = 'RP';
    component.onFieldCommitted('fund');
    component.model['idPart1'] = growerWithPhone.idNumber;
    component.onFieldCommitted('idPart1');
    component.model['special'] = 'RN';
    component.onFieldCommitted('special');
  }

  function fillWeightsAndBags(): void {
    component.model['totalStoredWeight'] = '220';
    component.onFieldCommitted('totalStoredWeight');
    component.model['totalHuskWeight'] = '20';
    component.onFieldCommitted('totalHuskWeight');
    component.model['healthyStoredWeight'] = '200';
    component.onFieldCommitted('healthyStoredWeight');
    component.model['bags'] = '10';
    component.onFieldCommitted('bags');
    component.model['grossKg'] = '1250';
    component.onFieldCommitted('grossKg');
  }

  it('never calls preview() while a REQUIRED field is still missing', () => {
    setup(growerWithPhone);
    fillCommon();
    fillWeightsAndBags();
    expect(previewSpy).not.toHaveBeenCalled();
    expect(component.calc()).toBeNull();
  });

  it('calls preview() as soon as the last REQUIRED field is committed', () => {
    setup(growerWithPhone);
    fillCommon();
    fillWeightsAndBags();
    component.model['tare'] = '50';
    component.onFieldCommitted('tare');

    expect(previewSpy).toHaveBeenCalledTimes(1);
    expect(component.calc()).toEqual(calculation);
  });

  it('REGRESIÓN: a grower with a blank migrated phone does not get cellphone permanently locked', () => {
    setup(growerWithoutPhone);
    component.model['fund'] = 'RP';
    component.onFieldCommitted('fund');
    component.model['idPart1'] = growerWithoutPhone.idNumber;
    component.onFieldCommitted('idPart1');

    expect((component as any).locked.has('cellphone')).toBe(false);
    expect(component.model['cellphone']).toBe('');

    component.model['cellphone'] = '3001112233';
    component.onFieldCommitted('cellphone');
    expect((component as any).locked.has('cellphone')).toBe(true);
  });

  it('shows the real backend error message instead of a generic one (e.g. cupo excedido, negativo, Cod_Prod inexistente)', () => {
    setup(growerWithPhone);
    previewSpy.mockReturnValue(
      throwError(() => ({ error: { message: 'No existe Cod_Prod para la combinacion Especial/Fondo indicada' } })),
    );
    fillCommon();
    fillWeightsAndBags();
    component.model['tare'] = '50';
    component.onFieldCommitted('tare');

    expect(component.calc()).toBeNull();
    expect(component.errorMessage()).toBe('No existe Cod_Prod para la combinacion Especial/Fondo indicada');
  });

  it('falls back to the generic message when the backend error has no message field', () => {
    setup(growerWithPhone);
    previewSpy.mockReturnValue(throwError(() => new Error('network down')));
    fillCommon();
    fillWeightsAndBags();
    component.model['tare'] = '50';
    component.onFieldCommitted('tare');

    expect(component.errorMessage()).toBe(content.messages['saveError']);
  });

  it('blocks the whole form when the grower is deceased', () => {
    setup({ ...growerWithPhone, deceased: true });
    component.model['fund'] = 'RP';
    component.onFieldCommitted('fund');
    component.model['idPart1'] = growerWithPhone.idNumber;
    component.onFieldCommitted('idPart1');

    expect(component.blockedByDeceased()).toBe(true);
    expect(component.errorMessage()).toBe(content.messages['deceasedBlocked']);
  });
});
