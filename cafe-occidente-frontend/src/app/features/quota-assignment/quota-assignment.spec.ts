import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';

import { QuotaAssignmentComponent } from './quota-assignment';
import { AnnouncementQuotaService } from '../../core/services/announcement-quota.service';
import { AnnouncementService } from '../../core/services/announcement.service';
import { ContentService } from '../../core/services/content.service';

describe('QuotaAssignmentComponent', () => {
  const page = {
    windowTitle: 'ASIGNACION CUPOS A ANUNCIOS',
    heading: 'Asignar Cupo a Anuncios',
    announcementLabel: 'Anuncio',
    saveButton: 'Guardar',
    createNotice: 'Este anuncio todavía no tiene cupo asignado.',
    savedMessage: 'Cupo guardado correctamente.',
    errorMessage: 'Ocurrió un error.',
    leftColumn: [
      { key: 'agency', label: 'Agencia', type: 'text' },
      { key: 'announcementDate', label: 'Fecha', type: 'date' },
      { key: 'special', label: 'Especial', type: 'text' },
      { key: 'basePriceCps', label: 'Pr Base CPS', type: 'currency' },
    ],
    rightColumn: [
      { key: 'healthyStoredPrice', label: 'Pr Sustentación', type: 'currency' },
      { key: 'defectStoredPrice', label: 'Pr Almendra Defec', type: 'currency' },
      { key: 'bonus', label: 'Bonificación', type: 'currency' },
      { key: 'costs', label: 'Costos', type: 'currency' },
      { key: 'deliveredKg', label: 'Kilos Entregados', type: 'number' },
      { key: 'quota', label: 'Cupo', type: 'number' },
      { key: 'balance', label: 'Saldo', type: 'number' },
    ],
  };

  const announcement = {
    id: 1,
    announcementNumber: 'SDBU-32',
    announcementDate: '2026-09-23',
    basePriceLoad: 1200000,
    defectiveUnitPrice: 0,
    healthyUnitPrice: 8908,
    bonus: 40,
    costs: 692,
    agencyId: 1,
    fundId: 1,
    specialType: 'RN',
  };

  let assignSpy: ReturnType<typeof vi.fn>;
  let getSpy: ReturnType<typeof vi.fn>;
  let component: QuotaAssignmentComponent;

  function setup() {
    assignSpy = vi.fn(() => of({ id: 9, agencyId: 1, announcementNumber: 32, announcementDate: '2026-09-23', specialType: 'RN', assignedQuota: 576.7, deliveredKg: 0, balance: 576.7 }));
    getSpy = vi.fn(() => throwError(() => ({ status: 404 })));

    localStorage.setItem('cafeoccidente.agencyId', '1');
    localStorage.setItem('cafeoccidente.agencyName', 'Buesaco');

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ContentService, useValue: { loadJson: () => of(page) } },
        { provide: AnnouncementService, useValue: { history: () => of([announcement]) } },
        { provide: AnnouncementQuotaService, useValue: { get: getSpy, assign: assignSpy } },
      ],
    });
    component = TestBed.createComponent(QuotaAssignmentComponent).componentInstance;
  }

  it('shows the agency from the session without letting the user pick it', () => {
    setup();
    expect(component.leftFields().find((f) => f.key === 'agency')?.value).toBe('Buesaco');
    expect(component.leftFields().find((f) => f.key === 'agency')?.readonly).toBe(true);
  });

  it('selecting an announcement without an existing quota flags isCreate and leaves Cupo editable', () => {
    setup();
    component.onAnnouncementChange('SDBU-32');

    expect(component.isCreate()).toBe(true);
    expect(component.announcementNumber()).toBe(32);
    expect(component.rightFields().find((f) => f.key === 'quota')?.readonly).toBe(false);
    expect(component.leftFields().find((f) => f.key === 'special')?.value).toBe('RN');
  });

  it('selecting an announcement with an existing quota prefills Cupo/Entregado/Saldo', () => {
    setup();
    getSpy.mockReturnValue(
      of({ id: 9, agencyId: 1, announcementNumber: 32, announcementDate: '2026-09-23', specialType: 'RN', assignedQuota: 576.7, deliveredKg: 200, balance: 376.7 }),
    );
    component.onAnnouncementChange('SDBU-32');

    expect(component.isCreate()).toBe(false);
    expect(component.rightFields().find((f) => f.key === 'quota')?.value).toBe('576.7');
    expect(component.rightFields().find((f) => f.key === 'deliveredKg')?.value).toBe('200');
  });

  it('save() sends the raw announcement number (not the prefixed display string) to assign()', () => {
    setup();
    component.onAnnouncementChange('SDBU-32');
    component.onFieldValueChange({ key: 'quota', value: '576.7' });

    component.save();

    expect(assignSpy).toHaveBeenCalledWith(1, 32, { assignedQuota: 576.7 });
  });

  it('shows the real backend error message on a failed save instead of a generic one', () => {
    setup();
    assignSpy.mockReturnValue(throwError(() => ({ error: { message: 'El cupo no puede ser negativo' } })));
    component.onAnnouncementChange('SDBU-32');
    component.onFieldValueChange({ key: 'quota', value: '-5' });

    component.save();

    expect(component.errorMessage()).toBe('El cupo no puede ser negativo');
  });
});
