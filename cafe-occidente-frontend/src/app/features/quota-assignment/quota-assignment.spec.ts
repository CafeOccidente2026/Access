import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';

import { QuotaAssignmentComponent } from './quota-assignment';
import { AnnouncementQuotaService } from '../../core/services/announcement-quota.service';
import { ContentService } from '../../core/services/content.service';

describe('QuotaAssignmentComponent', () => {
  const page = {
    windowTitle: 'ASIGNACION CUPOS A ANUNCIOS',
    heading: 'Asignar Cupo a Anuncios',
    announcementLabel: 'Anuncio',
    saveButton: 'Guardar',
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

  const candidate = {
    announcement: {
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
    },
    deliveredKg: 200,
  };
  const label = 'SDBU-32 · RN · 1200000';
  const saved = { id: 9, agencyId: 1, announcementNumber: 32, announcementDate: '2026-09-23', specialType: 'RN', assignedQuota: 576.7, deliveredKg: 200, balance: 376.7 };

  let assignSpy: ReturnType<typeof vi.fn>;
  let component: QuotaAssignmentComponent;

  function setup() {
    assignSpy = vi.fn(() => of(saved));
    localStorage.setItem('cafeoccidente.agencyId', '1');
    localStorage.setItem('cafeoccidente.agencyName', 'Buesaco');

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ContentService, useValue: { loadJson: () => of(page) } },
        { provide: AnnouncementQuotaService, useValue: { available: () => of([candidate]), assign: assignSpy } },
      ],
    });
    const fixture = TestBed.createComponent(QuotaAssignmentComponent);
    fixture.detectChanges();
    component = fixture.componentInstance;
  }

  const field = (key: string) => [...component.leftFields(), ...component.rightFields()].find((f) => f.key === key);

  it('shows the agency from the session without letting the user pick it', () => {
    setup();
    expect(field('agency')?.value).toBe('Buesaco');
    expect(field('agency')?.readonly).toBe(true);
  });

  it('CA-1: each option shows number, special and price so typing either one filters the list', () => {
    setup();
    expect(component.announcementField().options).toEqual([label]);
  });

  it('CA-2: choosing an announcement fills its data and Kilos Entregados, and only Cupo is editable', () => {
    setup();
    component.onAnnouncementChange(label);

    expect(field('special')?.value).toBe('RN');
    expect(field('basePriceCps')?.value).toBe('1200000');
    expect(field('deliveredKg')?.value).toBe('200');
    expect(field('quota')?.readonly).toBe(false);
    expect(field('deliveredKg')?.readonly).toBe(true);
  });

  it('CA-3: saving sends the raw number, shows the Saldo and takes the announcement out of the list', () => {
    setup();
    component.onAnnouncementChange(label);
    component.onFieldValueChange({ key: 'quota', value: '576,7' });

    component.save();

    expect(assignSpy).toHaveBeenCalledWith(32, { assignedQuota: 576.7 });
    expect(field('balance')?.value).toBe('376.7');
    expect(field('quota')?.readonly).toBe(true);
    expect(component.announcementField().options).toEqual([]);
    expect(component.savedMessage()).toBe('Cupo guardado correctamente.');
  });

  it('two clicks on Guardar send a single request', () => {
    setup();
    const pending = new Subject<typeof saved>();
    assignSpy.mockReturnValue(pending);
    component.onAnnouncementChange(label);
    component.onFieldValueChange({ key: 'quota', value: '500' });

    component.save();
    component.save();

    expect(assignSpy).toHaveBeenCalledTimes(1);
    expect(component.saving()).toBe(true);
  });

  it('shows the server message when the quota is rejected (below the kilos delivered)', () => {
    setup();
    assignSpy.mockReturnValue(throwError(() => ({ error: { message: 'El cupo no puede ser menor que los kilos ya entregados (200)' } })));
    component.onAnnouncementChange(label);
    component.onFieldValueChange({ key: 'quota', value: '150' });

    component.save();

    expect(component.errorMessage()).toContain('menor que los kilos ya entregados');
    expect(component.announcementField().options).toEqual([label]);
    expect(component.saving()).toBe(false);
  });

  it('save without an announcement does nothing', () => {
    setup();
    component.save();
    expect(assignSpy).not.toHaveBeenCalled();
  });
});
