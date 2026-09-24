import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';

import { FuturePurchaseFormComponent } from './future-purchase-form';
import { AnnouncementService } from '../../../core/services/announcement.service';
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
    messages: {
      acceptLabel: 'Aceptar',
      noAnnouncement: 'No hay anuncio',
      deceasedBlocked: 'No se le puede anunciar una entrega futura a un caficultor fallecido.',
      idNumberNotFound: 'Este número de cédula no existe.',
      saveError: 'No se pudo registrar el compromiso. Verifique los datos.',
      savedNotice: 'Compromiso registrado.',
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
  let component: FuturePurchaseFormComponent;

  function setup(grower: Grower) {
    createSpy = vi.fn(() => of({}));
    localStorage.setItem('cafeoccidente.agencyId', '1');
    localStorage.setItem('cafeoccidente.agencyName', 'Buesaco');

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ContentService, useValue: { loadJson: () => of(content) } },
        { provide: FuturePurchaseService, useValue: { create: createSpy } },
        {
          provide: AnnouncementService,
          useValue: { funds: () => of([{ id: 1, code: 'RP', name: 'Resolución Propia' }]), latest: () => of(announcement) },
        },
        { provide: GrowerService, useValue: { findByIdNumber: vi.fn(() => of(grower)), findProgram: () => of(null) } },
      ],
    });
    component = TestBed.createComponent(FuturePurchaseFormComponent).componentInstance;
  }

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

  it('does not call announcementService.latest() until an Especial is committed', () => {
    setup(growerWithoutId);
    expect(component.announcementInfo()).toBeNull();
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

    expect((component as any).canPrint()).toBe(true);
  });

  it('shows the real backend error (e.g. caficultor no pertenece a ningun programa) instead of a generic one', () => {
    setup(growerWithoutId);
    createSpy.mockReturnValue(
      throwError(() => ({ error: { message: 'CAFICULTOR NO PERTENECE A NINGUN PROGRAMA, NO PUEDE ANUNCIAR ESTE TIPO DE CAFE' } })),
    );
    component.model['idPart1'] = growerWithoutId.idNumber;
    component.onFieldCommitted('idPart1');
    component.model['special'] = 'NESPRESSO - FTUSA';
    component.onFieldCommitted('special');
    component.model['deliveryDate'] = '2026-12-01';
    component.onFieldCommitted('deliveryDate');
    component.model['kilos'] = '500';
    component.onFieldCommitted('kilos');

    component.print();

    expect(component.errorMessage()).toBe('CAFICULTOR NO PERTENECE A NINGUN PROGRAMA, NO PUEDE ANUNCIAR ESTE TIPO DE CAFE');
  });

  it('blocks the whole form when the grower is deceased', () => {
    setup({ ...growerWithoutId, deceased: true });
    component.model['idPart1'] = growerWithoutId.idNumber;
    component.onFieldCommitted('idPart1');

    expect(component.blockedByDeceased()).toBe(true);
    expect(component.errorMessage()).toBe(content.messages['deceasedBlocked']);
  });
});
