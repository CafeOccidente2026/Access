import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';

import { FertiFuturoFormComponent } from './ferti-futuro-form';
import { AnnouncementService } from '../../../core/services/announcement.service';
import { ContentService } from '../../../core/services/content.service';
import { FertiFuturoPurchaseService } from '../../../core/services/ferti-futuro-purchase.service';
import { GrowerService } from '../../../core/services/grower.service';
import { Grower } from '../../../core/models/grower.model';
import { PurchaseFormContent } from '../../../core/models/purchase-form-content.model';

describe('FertiFuturoFormComponent - gates de captura', () => {
  const content: PurchaseFormContent & { messages: Record<string, string> } = {
    windowTitle: 'FERTIFUTURO',
    theme: 'teal',
    topFields: [],
    identificationFields: [],
    messages: {
      acceptLabel: 'Aceptar',
      noAnnouncement: 'No hay anuncio',
      deceasedBlocked: 'No se le puede facturar a un fallecido.',
      idNumberNotFound: 'Este número de cédula no existe.',
      futurePurchaseNotFound: 'Ese compromiso no existe.',
      saveError: 'No se pudo registrar la liquidación. Verifique los datos.',
      savedNotice: 'Liquidación registrada.',
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

  const growerWithoutAddress: Grower = { ...growerWithPhone, idNumber: '27197019', address: '' };

  const announcement = {
    id: 1,
    announcementNumber: 'SDBU-1',
    announcementDate: '2026-09-23',
    basePriceLoad: 12000,
    defectiveUnitPrice: 0,
    healthyUnitPrice: 12000,
    bonus: 100,
    costs: 692,
    agencyId: 1,
    fundId: 1,
    specialType: 'RN',
  };

  const calculation = {
    tareKg: 50,
    healthyPercentage: 88,
    defectivePercentage: 8,
    qualityIncrementAmount: 6500,
    unitPrice: 12000,
    grossValue: 14400000,
    inventoryValue: 14400000,
    associateContribution: 0,
    cooperativeDiscount: 115200,
    withholding: 0,
    netToPay: 14284800,
  };

  let previewSpy: ReturnType<typeof vi.fn>;
  let component: FertiFuturoFormComponent;

  function setup(grower: Grower) {
    previewSpy = vi.fn(() => of(calculation));
    localStorage.setItem('cafeoccidente.agencyId', '1');
    localStorage.setItem('cafeoccidente.agencyName', 'Buesaco');

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ContentService, useValue: { loadJson: () => of(content) } },
        {
          provide: FertiFuturoPurchaseService,
          useValue: {
            funds: () => of([{ id: 1, code: 'RP', name: 'Resolución Propia' }]),
            nextInvoiceNumber: () => of({ invoiceNumber: 100, prefix: 'SDBU', warning: null }),
            preview: previewSpy,
            create: vi.fn(() => of({})),
            findFuturePurchase: vi.fn(() => of({ id: 5, remainingKg: 300 })),
          },
        },
        { provide: AnnouncementService, useValue: { latest: () => of(announcement) } },
        { provide: GrowerService, useValue: { findByIdNumber: vi.fn(() => of(grower)) } },
      ],
    });
    component = TestBed.createComponent(FertiFuturoFormComponent).componentInstance;
  }

  function fillRequired(): void {
    component.model['fund'] = 'RP';
    component.onFieldCommitted('fund');
    component.model['idPart1'] = growerWithPhone.idNumber;
    component.onFieldCommitted('idPart1');
    component.model['special'] = 'RN';
    component.onFieldCommitted('special');
    component.model['sacos'] = '10';
    component.onFieldCommitted('sacos');
    component.model['grossKg'] = '1250';
    component.onFieldCommitted('grossKg');
    component.model['netKg'] = '1200';
    component.onFieldCommitted('netKg');
    component.model['healthyStoredWeight'] = '220';
    component.onFieldCommitted('healthyStoredWeight');
  }

  it('never calls preview() while a REQUIRED field is still missing', () => {
    setup(growerWithPhone);
    component.model['fund'] = 'RP';
    component.onFieldCommitted('fund');
    component.model['idPart1'] = growerWithPhone.idNumber;
    component.onFieldCommitted('idPart1');
    expect(previewSpy).not.toHaveBeenCalled();
  });

  it('calls preview() once every REQUIRED field is committed, without needing futurePurchaseId', () => {
    setup(growerWithPhone);
    fillRequired();
    expect(previewSpy).toHaveBeenCalledTimes(1);
    expect(component.calc()).toEqual(calculation);
  });

  it('REGRESIÓN: a grower with a blank migrated field is not permanently locked out', () => {
    setup(growerWithoutAddress);
    component.model['idPart1'] = growerWithoutAddress.idNumber;
    component.onFieldCommitted('idPart1');

    expect((component as any).locked.has('address')).toBe(false);
    component.model['address'] = 'Escrito a mano';
    component.onFieldCommitted('address');
    expect((component as any).locked.has('address')).toBe(true);
  });

  it('looking up an optional compromiso shows its remaining balance without gating the REQUIRED fields', () => {
    setup(growerWithPhone);
    component.model['futurePurchaseId'] = '5';
    component.onFieldCommitted('futurePurchaseId');

    expect(component.model['futurePurchaseBalance']).toBe('300');
  });

  it('shows the real backend error (e.g. saldo de compromiso excedido) instead of a generic one', () => {
    setup(growerWithPhone);
    previewSpy.mockReturnValue(
      throwError(() => ({ error: { message: 'La entrega no debe ser superior al saldo pendiente del compromiso (saldo: 300 kg)' } })),
    );
    fillRequired();

    expect(component.errorMessage()).toBe('La entrega no debe ser superior al saldo pendiente del compromiso (saldo: 300 kg)');
  });

  it('blocks the whole form when the grower is deceased', () => {
    setup({ ...growerWithPhone, deceased: true });
    component.model['idPart1'] = growerWithPhone.idNumber;
    component.onFieldCommitted('idPart1');

    expect(component.blockedByDeceased()).toBe(true);
    expect(component.errorMessage()).toBe(content.messages['deceasedBlocked']);
  });
});
