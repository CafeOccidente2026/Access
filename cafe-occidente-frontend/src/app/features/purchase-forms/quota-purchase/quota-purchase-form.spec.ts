import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { Subject, of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';

import { QuotaPurchaseFormComponent } from './quota-purchase-form';
import { QuotaBalance } from '../../../core/models/announcement-quota.model';
import { AnnouncementQuotaService } from '../../../core/services/announcement-quota.service';
import { ContentService } from '../../../core/services/content.service';
import { DryCoffeePurchaseService } from '../../../core/services/dry-coffee-purchase.service';
import { FuturePurchaseService } from '../../../core/services/future-purchase.service';
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
    cooperativeDiscountLabel: 'Descuento Coop',
  };
  const content = {
    windowTitle: 'COMPRAS CUPOS',
    theme: 'green',
    topFields: [{ key: 'special', label: 'Especial', type: 'select', options: [] }],
    identificationFields: [{ key: 'deliveryKg', label: 'Kilos Entregados', type: 'number' }],
    federationFields: ['idNumber', 'fullName', 'program'].map((key) => ({ key, label: key, type: 'text' })),
    qualityFields: [{ key: 'healthyStoredWeight', label: 'Peso Alm Sana', type: 'currency' }],
    weightFields: [
      { key: 'grossKg', label: 'Kilos Brutos', type: 'number' },
      { key: 'tare', label: 'Destare', type: 'number' },
      { key: 'netKg', label: 'Kilos Netos', type: 'number' },
    ],
    settlementSecondaryFields: [{ key: 'withholding', label: 'Retefuente', type: 'currency' }],
    additionalDiscountFields: [{ key: 'contribution', label: 'Aporte Socio', type: 'currency' }],
    messages,
    quotaPicker: {},
  } as unknown as PurchaseFormContent;

  const drySpecials = { qualityFields: [{ key: 'special', options: ['RN', 'COLOMBIA TIERRA DE DIVERSIDAD RN'] }] };

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

  /** Compra anunciada de 2500 kg con 1500 por entregar. */
  const futurePurchase = {
    id: 55, agencyId: 1, agencyName: 'Buesaco 1', announcementNumber: 'SDBU-4', announcementDate: '2026-11-01',
    idNumber: '990514', firstName: 'CAFICULTOR', lastName: 'DOS', growerType: 'C', address: 'VEREDA',
    specialType: 'NESPRESSO - FTUSA', announcedKg: 2500, remainingKg: 1500, deliveryDate: '2026-12-01', finca: null,
    municipality: null, vereda: null, healthyUnitPrice: 9100, defectiveUnitPrice: 6000, bonus: 30, costs: 692,
    qualityIncrement: 0, basePriceLoad: 1500000,
  };

  let createSpy: ReturnType<typeof vi.fn>;
  let previewSpy: ReturnType<typeof vi.fn>;
  let specialInfoSpy: ReturnType<typeof vi.fn>;
  let withBalanceSpy: ReturnType<typeof vi.fn>;
  let openForSpy: ReturnType<typeof vi.fn>;
  let qualitySpy: ReturnType<typeof vi.fn>;
  let component: QuotaPurchaseFormComponent;

  function setup(mode: 'quota' | 'future' = 'quota') {
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
    openForSpy = vi.fn(() => of([futurePurchase]));
    createSpy = vi.fn(() => of({}));
    previewSpy = vi.fn(() => of({ netKg: 1000, unitPrice: 7218, grossValue: 7218000, netToPay: 7100000 }));
    localStorage.setItem('cafeoccidente.agencyName', 'Buesaco 1');
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: ContentService,
          // Especial sale de la lista de Compras Cafe Seco (prueba del 9-nov, punto 3).
          useValue: { loadJson: (name: string) => of(name === 'purchase-form-dry' ? drySpecials : content) },
        },
        {
          provide: DryCoffeePurchaseService,
          useValue: {
            funds: () => of([{ id: 1, code: 'RP', name: 'Resolución Propia' }]),
            nextInvoiceNumber: () => of({ invoiceNumber: 27872, prefix: 'SDBU', warning: null }),
            specialInfo: specialInfoSpy,
            qualityPercentages: qualitySpy,
            preview: previewSpy,
            create: createSpy,
          },
        },
        { provide: AnnouncementQuotaService, useValue: { withBalance: withBalanceSpy } },
        { provide: FuturePurchaseService, useValue: { openFor: openForSpy } },
        { provide: ActivatedRoute, useValue: { snapshot: { data: { mode } } } },
        {
          provide: GrowerService,
          useValue: {
            findByIdNumber: () => of({ idNumber: '990514', firstName: 'CAFICULTOR', lastName: 'DOS', growerType: 'C',
              address: 'VEREDA', phone: '3001112233' }),
            findProgram: vi.fn(() => of(null)),
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
    commit('idPart1', '990514');
    for (const [key, value] of [['totalStoredWeight', '200'], ['totalHuskWeight', '10'], ['bags', '5'],
      ['grossKg', '1000'], ['tare', '0'], ['penalty', '0'], ['shrinkageDiscount', '0'], ['otherDiscounts', '0']]) {
      commit(key, value);
    }
    component.payment.set({ cashAmount: 7100000, checkAmount: 0, transferAmount: 0, cardAmount: 0, checkNumber: null });
  }

  const field = (key: string) =>
    [...(component.viewContent()?.identificationFields ?? []), ...(component.viewContent()?.qualityFields ?? [])]
      .find((f) => f.key === key);

  /** Busca el campo en todos los bloques de la pantalla. */
  const anyField = (key: string): { readonly?: boolean; value?: unknown } | undefined => {
    type Field = { key?: string; readonly?: boolean; value?: unknown };
    const find = (node: unknown): Field | undefined => {
      if (Array.isArray(node)) return node.map(find).find(Boolean);
      if (node === null || typeof node !== 'object') return undefined;
      if ((node as Field).key === key) return node as Field;
      return Object.values(node).map(find).find(Boolean);
    };
    return find(component.viewContent());
  };

  it('prueba del 11-nov (punto 9): Destare y Kilos Netos salen de Kilos Brutos y Esta Entrega, no se digitan', () => {
    setup();
    pickQuota(); // Esta Entrega 1000
    commit('grossKg', '1005');
    expect(anyField('tare')).toMatchObject({ readonly: true, value: 5 });
    expect(anyField('netKg')?.value).toBe(1000);

    // Entrega parcial: los brutos no alcanzan la entrega.
    commit('grossKg', '900');
    expect(anyField('tare')?.value).toBe(0);
    expect(anyField('netKg')?.value).toBe(900);

    // Lo que se manda a calcular lleva ese destare (el servidor lo vuelve a calcular igual).
    fillPurchase();
    commit('grossKg', '1005');
    expect(previewSpy).toHaveBeenLastCalledWith(expect.objectContaining({ grossKg: 1005, tareKg: 5, deliveryKg: 1000 }));
  });

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

  it('prueba del 11-nov (punto 4): failed quota list or announcement shows the reason of the server', () => {
    setup();
    withBalanceSpy.mockReturnValue(throwError(() => ({ error: { message: 'Fondo no encontrado' } })));
    commit('fund', 'RP');
    commit('special', 'RN');
    expect(component.errorMessage()).toBe('Fondo no encontrado');

    withBalanceSpy.mockReturnValue(of([quotaRow]));
    specialInfoSpy.mockReturnValue(throwError(() => ({ error: { message: 'No hay un anuncio de precio vigente para esa agencia/fondo/especial' } })));
    pickQuota();
    expect(component.errorMessage()).toBe('No hay un anuncio de precio vigente para esa agencia/fondo/especial');
  });

  it('CA-2: the chosen announcement brings its own prices and Esta Entrega shows as Kilos Entregados', () => {
    setup();
    pickQuota();

    expect(component.quotaRows()).toBeNull();
    expect(specialInfoSpy).toHaveBeenCalledWith(1, 'RN', 12);
    expect(field('deliveryKg')?.value).toBe(1000);
  });

  it('C3: the Federación block takes NESS only and is read only; without NESS it stays empty', () => {
    setup();
    (TestBed.inject(GrowerService).findProgram as ReturnType<typeof vi.fn>).mockReturnValue(of({ programa: 'NESPRESSO', cupo: '200', cedula: '990514', nombres: 'CAFICULTOR DOS NESS' }));
    commit('idPart1', '990514');
    const federation = (key: string) => component.viewContent()!.federationFields!.find((f) => f.key === key)!;
    for (const [key, value] of Object.entries({ idNumber: '990514', fullName: 'CAFICULTOR DOS NESS', program: 'NESPRESSO' })) {
      expect(federation(key)).toMatchObject({ value, readonly: true });
    }

    TestBed.resetTestingModule();
    setup();
    commit('idPart1', '990514');
    for (const key of ['idNumber', 'fullName', 'program']) {
      expect(federation(key)).toMatchObject({ value: '', readonly: true });
    }
    expect(component.model['firstNames']).toBe('CAFICULTOR');
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
    expect(createSpy.mock.calls[0][0]).toMatchObject({ quotaAnnouncementNumber: 12, deliveryKg: 1000, specialType: 'RN' });
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

  it('the preview already says when the net kilos pass Esta Entrega', () => {
    setup();
    previewSpy.mockReturnValue(throwError(() => ({ error: { message: 'Los kilos netos (1000) superan Esta Entrega (500)' } })));
    fillPurchase();

    expect(component.errorMessage()).toBe('Los kilos netos (1000) superan Esta Entrega (500)');
    expect(component.calc()).toBeNull();
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

  it('RN-15.6: Retefuente is read-only and shows what the server calculated', () => {
    setup();
    previewSpy.mockReturnValue(of({ netKg: 1000, unitPrice: 7218, grossValue: 7218000, withholding: 6000, netToPay: 7100000 }));
    const withholding = () => component.viewContent()!.settlementSecondaryFields!.find((f) => f.key === 'withholding')!;
    expect(withholding().readonly).toBe(true);

    fillPurchase();

    expect(withholding().readonly).toBe(true);
    expect(withholding().value).toBe(6000);
  });

  it('Especial offers the same list as Compras Café Seco (COLOMBIA TIERRA DE DIVERSIDAD RN included)', () => {
    setup();
    const special = component.viewContent()!.topFields.find((f) => f.key === 'special')!;
    expect(special.options).toEqual(['RN', 'COLOMBIA TIERRA DE DIVERSIDAD RN']);
  });

  it('Q15 (prueba del 9-nov, punto 9): over the threshold it warns; Aceptar clears the sample and goes back to Peso Tot Alm', () => {
    setup();
    const focusSpy = vi.fn();
    (component as any).focusField = focusSpy;
    const message = 'El Factor (94,59) supera el umbral de cafés especiales (93,33). Digite de nuevo los pesos de la muestra.';
    previewSpy.mockReturnValue(throwError(() => ({ error: { code: 'UMBRAL_ESPECIALES', message } })));
    fillPurchase();

    expect(component.errorMessage()).toBe(message);
    // El foco no vuelve a la muestra detras del aviso: eso pasa al Aceptar.
    expect(focusSpy.mock.lastCall?.[0]).not.toBe('totalStoredWeight');

    component.dismissError();

    for (const key of ['totalStoredWeight', 'totalHuskWeight', 'healthyStoredWeight', 'wastePercentage', 'huskPercentage', 'factor']) {
      expect(component.model[key] ?? '', key).toBe('');
      expect((component as any).locked.has(key), key).toBe(false);
    }
    expect(component.calc()).toBeNull();
    expect(focusSpy).toHaveBeenLastCalledWith('totalStoredWeight');
  });

  it('Escape clears the chosen announcement too', () => {
    setup();
    pickQuota();
    component.reset();
    expect(component.quotaChoice()).toBeNull();
    expect(field('deliveryKg')?.value).toBe('');
  });

  describe('Facturar Compras Anunciadas (RF-21.5)', () => {
    function pickFuture(): void {
      commit('fund', 'RP');
      commit('idPart1', '990514');
      component.onQuotaPicked({ row: component.quotaRows()![0], deliveryKg: 1000 });
    }

    it('CA-1: the cédula opens the window with the open future purchases of that person', () => {
      setup('future');
      commit('fund', 'RP');
      commit('idPart1', '990514');

      expect(openForSpy).toHaveBeenCalledWith('990514');
      const row = component.quotaRows()![0];
      expect(row.announcement.id).toBe(55);
      expect([row.assignedQuota, row.deliveredKg, row.balance]).toEqual([2500, 1000, 1500]);
      expect(withBalanceSpy).not.toHaveBeenCalled();
    });

    it('prueba del 11-nov (punto 4): a failed search of future purchases shows the reason of the server', () => {
      setup('future');
      openForSpy.mockReturnValue(throwError(() => ({ error: { message: 'Tercero no encontrado' } })));
      commit('fund', 'RP');
      commit('idPart1', '990514');

      expect(component.errorMessage()).toBe('Tercero no encontrado');
    });

    it('without open future purchases it warns and frees the cédula', () => {
      setup('future');
      openForSpy.mockReturnValue(of([]));
      commit('fund', 'RP');
      commit('idPart1', '990514');

      expect(component.errorMessage()).toBe(messages.noQuota);
      expect(component.model['idPart1']).toBe('');
    });

    it('CA-2: the chosen one brings its special and its own prices; the special is not typed', () => {
      setup('future');
      pickFuture();

      expect(component.model['special']).toBe('NESPRESSO - FTUSA');
      expect(specialInfoSpy).toHaveBeenCalledWith(1, 'NESPRESSO - FTUSA', undefined, 55);
      expect(field('deliveryKg')?.value).toBe(1000);
    });

    it('CA-2: Imprimir sends the future purchase and Esta Entrega, once', () => {
      setup('future');
      pickFuture();
      for (const [key, value] of [['totalStoredWeight', '200'], ['totalHuskWeight', '10'], ['bags', '5'],
        ['grossKg', '1000'], ['tare', '0'], ['penalty', '0'], ['shrinkageDiscount', '0'], ['otherDiscounts', '0']]) {
        commit(key, value);
      }
      component.payment.set({ cashAmount: 7100000, checkAmount: 0, transferAmount: 0, cardAmount: 0, checkNumber: null });
      const pending = new Subject<never>();
      createSpy.mockReturnValue(pending);

      component.print();
      component.print();

      expect(createSpy).toHaveBeenCalledTimes(1);
      const request = createSpy.mock.calls[0][0];
      expect(request).toMatchObject({ futurePurchaseId: 55, deliveryKg: 1000, specialType: 'NESPRESSO - FTUSA', idNumber: '990514' });
      expect(request).not.toHaveProperty('quotaAnnouncementNumber');
    });

    it('CA-4: cancelling the window frees the cédula and nothing is saved', () => {
      setup('future');
      commit('fund', 'RP');
      commit('idPart1', '990514');
      component.onQuotaCancelled();

      expect(component.quotaRows()).toBeNull();
      expect(component.model['idPart1']).toBe('');
      expect(createSpy).not.toHaveBeenCalled();
    });
  });

  it('prueba del 10-nov: with a type C grower the contribution says Descuento Coop (cupos and anunciadas)', () => {
    for (const mode of ['quota', 'future'] as const) {
      TestBed.resetTestingModule();
      setup(mode);
      const contribution = () => component.viewContent()?.additionalDiscountFields?.find((f) => f.key === 'contribution');
      expect(contribution()?.label).toBe('Aporte Socio');
      commit('fund', 'RP');
      commit('idPart1', '990514');
      expect(contribution()?.label).toBe('Descuento Coop');
    }
  });
});
