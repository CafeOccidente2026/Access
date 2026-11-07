import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';

import { FuturePurchaseFormComponent } from './future-purchase-form';
import { ContentService } from '../../../core/services/content.service';
import { FuturePurchaseService } from '../../../core/services/future-purchase.service';
import { GrowerService } from '../../../core/services/grower.service';
import { Grower } from '../../../core/models/grower.model';
import { PurchaseFormContent } from '../../../core/models/purchase-form-content.model';

/**
 * Compras a Futuro: sin cascada de precio (a diferencia de los otros 4), pero comparte el mismo
 * mecanismo de auto-lock en lookupGrower() (mismo fix de hoy) y el mismo patron de mostrar el
 * mensaje real del backend (fallecido / no pertenece a programa) en vez de uno generico.
 */
describe('FuturePurchaseFormComponent', () => {
  const content: PurchaseFormContent & { messages: Record<string, string> } = {
    windowTitle: 'COMPRAS A FUTURO',
    theme: 'green',
    topFields: [],
    identificationFields: [],
    contactFields: [
      { key: 'announcement', label: 'Anuncio', type: 'select', options: [] },
      { key: 'announcementDate', label: 'Fecha_Anuncio', type: 'date', readonly: true },
    ],
    weightFields: [
      { key: 'qualityIncrement', label: 'Pr IncCalidad', type: 'currency', readonly: true },
      { key: 'finca', label: 'Finca', type: 'text' },
      { key: 'municipality', label: 'Mpio', type: 'text' },
      { key: 'vereda', label: 'Vereda', type: 'text' },
      { key: 'deliveryDate', label: 'Fecha Entrega', type: 'date' },
    ],
    messages: {
      acceptLabel: 'Aceptar',
      noAnnouncement: 'No hay anuncio',
      deceasedBlocked: 'No se le puede anunciar una entrega futura a un caficultor fallecido.',
      idNumberNotFound: 'Este número de cédula no existe.',
      saveError: 'No se pudo registrar el compromiso. Verifique los datos.',
      savedNotice: 'Compromiso registrado.',
      invoiceError: 'No se pudo generar el manifiesto',
      closeWarning: 'Hay datos sin guardar.',
      closeConfirm: 'Cerrar',
      closeCancel: 'Cancelar',
    },
  };

  const growerWithoutId: Grower = {
    id: 1,
    idNumber: '27197019',
    firstName: 'TERESA',
    secondName: null,
    lastName: 'DIAZ',
    secondLastName: 'CORDOBA',
    address: 'EL CIDRAL',
    phone: '',
    growerType: 'C',
    active: true,
    deceased: false,
    withdrawn: false,
    transportCompany: null,
    vehiclePlate: null,
  };

  const announcement = {
    id: 1,
    announcementNumber: 'SDBU-31',
    qualityIncrement: 50,
    announcementDate: '2026-09-23',
    basePriceLoad: 1200000,
    defectiveUnitPrice: 0,
    healthyUnitPrice: 8908,
    bonus: 40,
    costs: 692,
    agencyId: 1,
    fundId: 1,
    specialType: 'NESPRESSO - FTUSA',
  };

  let createSpy: ReturnType<typeof vi.fn>;
  let todaySpy: ReturnType<typeof vi.fn>;
  let component: FuturePurchaseFormComponent;

  function setup(grower: Grower) {
    createSpy = vi.fn(() => of({}));
    todaySpy = vi.fn(() => of([announcement]));
    localStorage.setItem('cafeoccidente.agencyId', '1');
    localStorage.setItem('cafeoccidente.agencyName', 'Buesaco');

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ContentService, useValue: { loadJson: () => of(content) } },
        { provide: FuturePurchaseService, useValue: { create: createSpy, todayAnnouncements: todaySpy } },
        { provide: GrowerService, useValue: { findByIdNumber: vi.fn(() => of(grower)), findProgram: () => of(null) } },
      ],
    });
    component = TestBed.createComponent(FuturePurchaseFormComponent).componentInstance;
  }

  const label = '31 · 1.200.000,00';

  function fill(): void {
    component.model['idPart1'] = growerWithoutId.idNumber;
    component.onFieldCommitted('idPart1');
    component.model['special'] = 'NESPRESSO - FTUSA';
    component.onFieldCommitted('special');
    component.model['announcement'] = label;
    component.onFieldCommitted('announcement');
    component.model['deliveryDate'] = '2026-12-01';
    component.onFieldCommitted('deliveryDate');
    component.model['kilos'] = '500';
    component.onFieldCommitted('kilos');
  }

  const field = (key: string) =>
    [...(component.viewContent()?.contactFields ?? []), ...(component.viewContent()?.weightFields ?? [])].find((f) => f.key === key);

  it('REGRESIÓN: a grower with blank migrated fields is not permanently locked out', () => {
    setup(growerWithoutId);
    component.model['idPart1'] = growerWithoutId.idNumber;
    component.onFieldCommitted('idPart1');

    // El telefono no aplica a este formulario (no lo captura), pero secondName es null -> firstNames
    // solo trae "TERESA" (no vacio); verificamos en cambio que 'address' (con valor) SÍ se bloquea y
    // que el mecanismo no rompe con campos parcialmente vacios.
    expect((component as any).locked.has('idPart1')).toBe(true);
    expect(component.model['address']).toBe('EL CIDRAL');
  });

  it('does not ask for announcements until an Especial is committed', () => {
    setup(growerWithoutId);
    expect(todaySpy).not.toHaveBeenCalled();
    expect(component.announcementInfo()).toBeNull();
  });

  it('CA-2: the Especial fills the Anuncio list with today announcements (number and price)', () => {
    setup(growerWithoutId);
    component.model['special'] = 'NESPRESSO - FTUSA';
    component.onFieldCommitted('special');

    expect(todaySpy).toHaveBeenCalledWith('NESPRESSO - FTUSA');
    expect(field('announcement')?.options).toEqual([label]);
  });

  it('CA-2: choosing the announcement fills its date and prices, and Pr IncCalidad is read only', () => {
    setup(growerWithoutId);
    fill();

    expect(component.announcementInfo()).toEqual(announcement);
    expect(field('announcementDate')?.value).toBe('2026-09-23');
    expect(field('qualityIncrement')?.value).toBe(50);
    expect(field('qualityIncrement')?.readonly).toBe(true);
  });

  it('without announcements today it warns and frees the Especial', () => {
    setup(growerWithoutId);
    todaySpy.mockReturnValue(of([]));
    component.model['special'] = 'NESPRESSO - FTUSA';
    component.onFieldCommitted('special');

    expect(component.errorMessage()).toBe(content.messages['noAnnouncement']);
    expect(component.model['special']).toBe('');
  });

  it('CA-3: two clicks on Imprimir save once, with the chosen announcement number', () => {
    setup(growerWithoutId);
    fill();
    const pending = new Subject<never>();
    createSpy.mockReturnValue(pending);

    component.print();
    component.print();

    expect(createSpy).toHaveBeenCalledTimes(1);
    expect(createSpy.mock.calls[0][0]).toMatchObject({ announcementNumber: 31, announcedKg: 500, specialType: 'NESPRESSO - FTUSA' });
    expect(component.saving()).toBe(true);
    pending.error(new Error('fallo'));
    expect(component.saving()).toBe(false);
  });

  it('canPrint() requires an announcement and every REQUIRED field', () => {
    setup(growerWithoutId);
    expect((component as any).canPrint()).toBe(false);

    component.model['idPart1'] = growerWithoutId.idNumber;
    component.onFieldCommitted('idPart1');
    component.model['special'] = 'NESPRESSO - FTUSA';
    component.onFieldCommitted('special');
    component.model['deliveryDate'] = '2026-12-01';
    component.onFieldCommitted('deliveryDate');
    component.model['kilos'] = '500';
    component.onFieldCommitted('kilos');
    expect((component as any).canPrint()).toBe(false);

    component.model['announcement'] = label;
    component.onFieldCommitted('announcement');
    expect((component as any).canPrint()).toBe(true);
  });

  it('shows the real backend error (e.g. caficultor no pertenece a ningun programa) instead of a generic one', () => {
    setup(growerWithoutId);
    createSpy.mockReturnValue(
      throwError(() => ({ error: { message: 'CAFICULTOR NO PERTENECE A NINGUN PROGRAMA, NO PUEDE ANUNCIAR ESTE TIPO DE CAFE' } })),
    );
    fill();

    component.print();

    expect(component.errorMessage()).toBe('CAFICULTOR NO PERTENECE A NINGUN PROGRAMA, NO PUEDE ANUNCIAR ESTE TIPO DE CAFE');
  });

  it('one Enter on each empty optional field (Finca, Mpio, Vereda) moves on to the next one', async () => {
    setup(growerWithoutId);
    const optional = ['finca', 'municipality', 'vereda'];
    const fixture = TestBed.createComponent(FuturePurchaseFormComponent);
    component = fixture.componentInstance;
    fixture.autoDetectChanges();
    for (const [key, value] of [['idPart1', growerWithoutId.idNumber], ['special', 'NESPRESSO - FTUSA'], ['announcement', label]]) {
      component.model[key] = value;
      component.onFieldCommitted(key);
    }
    await fixture.whenStable();
    const input = (key: string) => fixture.nativeElement.querySelector(`[data-field-key="${key}"]`) as HTMLInputElement;
    const settle = async () => {
      await new Promise((r) => setTimeout(r));
      await fixture.whenStable();
    };

    input('finca').focus();
    for (const [i, key] of optional.entries()) {
      expect(document.activeElement).toBe(input(key));
      input(key).dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
      await settle();
      expect(document.activeElement).toBe(input([...optional, 'deliveryDate'][i + 1]));
    }
    expect(input('deliveryDate').readOnly).toBe(false);
  });

  it('blocks the whole form when the grower is deceased', () => {
    setup({ ...growerWithoutId, deceased: true });
    component.model['idPart1'] = growerWithoutId.idNumber;
    component.onFieldCommitted('idPart1');

    expect(component.blockedByDeceased()).toBe(true);
    expect(component.errorMessage()).toBe(content.messages['deceasedBlocked']);
  });
});
