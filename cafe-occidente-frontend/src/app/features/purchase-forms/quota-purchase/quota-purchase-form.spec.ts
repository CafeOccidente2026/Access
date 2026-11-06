import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';

import { QuotaPurchaseFormComponent } from './quota-purchase-form';
import { QuotaBalance } from '../../../core/models/announcement-quota.model';
import { AnnouncementQuotaService } from '../../../core/services/announcement-quota.service';
import { ContentService } from '../../../core/services/content.service';
import { DryCoffeePurchaseService } from '../../../core/services/dry-coffee-purchase.service';
import { GrowerService } from '../../../core/services/grower.service';
import { PurchaseFormContent } from '../../../core/models/purchase-form-content.model';

describe('QuotaPurchaseFormComponent', () => {
  const messages = {
    acceptLabel: 'Aceptar',
    noQuota: 'No hay anuncios con cupo para ese fondo y especial.',
    idNumberNotFound: 'Este número de cédula no existe.',
    saveError: 'No se pudo registrar la compra.',
    savedNotice: 'Compra registrada.',
    invoiceError: 'No se pudo generar la factura',
    closeWarning: 'Hay datos sin guardar.',
    closeConfirm: 'Cerrar',
    closeCancel: 'Cancelar',
  };
  const content = {
    windowTitle: 'COMPRAS CUPOS',
    theme: 'green',
    topFields: [],
    identificationFields: [{ key: 'deliveryKg', label: 'Kilos Entregados', type: 'number' }],
    qualityFields: [{ key: 'healthyStoredWeight', label: 'Peso Alm Sana', type: 'currency' }],
    messages,
    quotaPicker: {},
  } as unknown as PurchaseFormContent;

  const quotaRow: QuotaBalance = {
    announcement: {
      id: 7, announcementNumber: 'SDBU-12', announcementDate: '2026-11-02', basePriceLoad: 1546900,
      defectiveUnitPrice: 6000, healthyUnitPrice: 8900, bonus: 40, costs: 692, agencyId: 1, fundId: 1,
      specialType: 'RN', qualityIncrement: 50,
    },
    assignedQuota: 2500,
    deliveredKg: 1000,
    balance: 1500,
  };

  let createSpy: ReturnType<typeof vi.fn>;
  let specialInfoSpy: ReturnType<typeof vi.fn>;
  let withBalanceSpy: ReturnType<typeof vi.fn>;
  let qualitySpy: ReturnType<typeof vi.fn>;
  let component: QuotaPurchaseFormComponent;

  function setup() {
    qualitySpy = vi.fn((total?: number, husk?: number) =>
      of(
        husk === undefined
          ? { wastePercentage: 20, defectivePercentage: null, healthyPercentage: null }
          : { wastePercentage: null, defectivePercentage: 4, healthyStoredWeight: 190, healthyPercentage: 92.11 },
      ),
    );
    specialInfoSpy = vi.fn(() => of({ productCode: '011', announcementNumber: 'SDBU-12', announcementDate: '2026-11-02',
      basePriceLoad: 1100000, defectiveUnitPrice: 6000, healthyUnitPrice: 8900, bonus: 40, costs: 692 }));
    withBalanceSpy = vi.fn(() => of([quotaRow]));
    createSpy = vi.fn(() => of({}));
    localStorage.setItem('cafeoccidente.agencyName', 'Buesaco 1');
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ContentService, useValue: { loadJson: () => of(content) } },
        {
          provide: DryCoffeePurchaseService,
          useValue: {
            funds: () => of([{ id: 1, code: 'RP', name: 'Resolución Propia' }]),
            nextInvoiceNumber: () => of({ invoiceNumber: 27872, prefix: 'SDBU', warning: null }),
            specialInfo: specialInfoSpy,
            qualityPercentages: qualitySpy,
            preview: () => of({ netKg: 1000, unitPrice: 7218, grossValue: 7218000, netToPay: 7100000 }),
            create: createSpy,
          },
        },
        { provide: AnnouncementQuotaService, useValue: { withBalance: withBalanceSpy } },
        {
          provide: GrowerService,
          useValue: {
            findByIdNumber: () => of({ idNumber: '210514', firstName: 'PAZ', lastName: 'OSSA', growerType: 'C',
              address: 'VEREDA', phone: '3001112233' }),
            findProgram: () => of(null),
          },
        },
      ],
    });
    component = TestBed.createComponent(QuotaPurchaseFormComponent).componentInstance;
  }

  function commit(key: string, value: string): void {
    component.model[key] = value;
    component.onFieldCommitted(key);
  }

  function pickQuota(): void {
    commit('fund', 'RP');
    commit('special', 'RN');
    component.onQuotaPicked({ row: quotaRow, deliveryKg: 1000 });
  }

  function fillPurchase(): void {
    pickQuota();
    commit('idPart1', '210514');
    for (const [key, value] of [['totalStoredWeight', '200'], ['totalHuskWeight', '10'], ['bags', '5'],
      ['grossKg', '1000'], ['tare', '0'], ['penalty', '0'], ['shrinkageDiscount', '0'], ['otherDiscounts', '0']]) {
      commit(key, value);
    }
    component.payment.set({ cashAmount: 7100000, checkAmount: 0, transferAmount: 0, cardAmount: 0, checkNumber: null });
  }

  const field = (key: string) =>
    [...(component.viewContent()?.identificationFields ?? []), ...(component.viewContent()?.qualityFields ?? [])]
      .find((f) => f.key === key);

  it('CA-1: choosing the special opens the window with the quota announcements of that fund and special', () => {
    setup();
    commit('fund', 'RP');
    commit('special', 'RN');

    expect(withBalanceSpy).toHaveBeenCalledWith(1, 'RN');
    expect(component.quotaRows()).toEqual([quotaRow]);
  });

  it('without quota announcements it warns and frees the special', () => {
    setup();
    withBalanceSpy.mockReturnValue(of([]));
    commit('fund', 'RP');
    commit('special', 'RN');

    expect(component.quotaRows()).toBeNull();
    expect(component.errorMessage()).toBe(messages.noQuota);
    expect(component.model['special']).toBe('');
  });

  it('CA-2: the chosen announcement brings its own prices and Esta Entrega shows as Kilos Entregados', () => {
    setup();
    pickQuota();

    expect(component.quotaRows()).toBeNull();
    expect(specialInfoSpy).toHaveBeenCalledWith(1, 'RN', 12);
    expect(field('deliveryKg')?.value).toBe(1000);
  });

  it('cancelling the window leaves no announcement chosen', () => {
    setup();
    commit('fund', 'RP');
    commit('special', 'RN');
    component.onQuotaCancelled();

    expect(component.quotaRows()).toBeNull();
    expect(component.quotaChoice()).toBeNull();
    expect(component.model['special']).toBe('');
  });

  it('CA-4: two clicks on Imprimir save once, with the chosen announcement and Esta Entrega', () => {
    setup();
    fillPurchase();
    const pending = new Subject<never>();
    createSpy.mockReturnValue(pending);

    component.print();
    component.print();

    expect(createSpy).toHaveBeenCalledTimes(1);
    expect(createSpy.mock.calls[0][0]).toMatchObject({ quotaAnnouncementNumber: 12, quotaDeliveryKg: 1000, specialType: 'RN' });
    expect(component.saving()).toBe(true);
    pending.error(new Error('fallo'));
    expect(component.saving()).toBe(false);
  });

  it('a rejected quota shows the server reason', () => {
    setup();
    fillPurchase();
    createSpy.mockReturnValue(throwError(() => ({ error: { message: 'Los kilos netos (1000) superan Esta Entrega (900)' } })));

    component.print();

    expect(component.errorMessage()).toBe('Los kilos netos (1000) superan Esta Entrega (900)');
  });

  it('Imprimir does nothing until an announcement with quota is chosen', () => {
    setup();
    component.print();
    expect(createSpy).not.toHaveBeenCalled();
  });

  it('CA-5: with data, leaving asks first (the window and nothing else is saved)', () => {
    setup();
    commit('fund', 'RP');
    expect(component.canDeactivate()).toBeInstanceOf(Promise);
    expect(component.pendingClose()).toBe(true);
    component.cancelClose();
    expect(createSpy).not.toHaveBeenCalled();
  });

  it('Q2: the healthy almond comes calculated from the total and husk weights', () => {
    setup();
    commit('totalStoredWeight', '200');
    commit('totalHuskWeight', '10');

    expect(qualitySpy).toHaveBeenLastCalledWith(200, 10);
    expect(component.model['healthyStoredWeight']).toBe('190');
    expect(field('healthyStoredWeight')?.readonly).toBe(true);
    expect(field('healthyStoredWeight')?.value).toBe('190');
  });

  it('Escape clears the chosen announcement too', () => {
    setup();
    pickQuota();
    component.reset();
    expect(component.quotaChoice()).toBeNull();
    expect(field('deliveryKg')?.value).toBe('');
  });
});
