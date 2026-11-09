import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';

import { QuotaAssignmentComponent } from './quota-assignment';
import { AnnouncementQuotaService } from '../../core/services/announcement-quota.service';
import { ContentService } from '../../core/services/content.service';
import quotaJson from '../../../../public/assets/data/quota-assignment.json';

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
      { key: 'quota', label: 'Cupo', type: 'number', liveThousands: true },
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
    expect(component.announcementOptions()).toEqual([label]);
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
    expect(component.announcementOptions()).toEqual([]);
    expect(component.savedMessage()).toBe('Cupo guardado correctamente.');
  });

  it('typing in Cupo keeps the same box and its focus, digit after digit (prueba del 9-nov)', () => {
    setup();
    const fixture = TestBed.createComponent(QuotaAssignmentComponent);
    document.body.appendChild(fixture.nativeElement);
    fixture.detectChanges();
    fixture.componentInstance.onAnnouncementChange(label);
    fixture.detectChanges();
    const box = () => (fixture.nativeElement as HTMLElement).querySelector<HTMLInputElement>('[data-field-key=quota]')!;
    const first = box();
    first.focus();

    for (const typed of ['4', '40', '400', '4000']) {
      box().value = typed;
      box().dispatchEvent(new Event('input'));
      fixture.detectChanges();
    }

    expect(box()).toBe(first);
    expect(document.activeElement).toBe(first);
    expect(first.value).toBe('4000');
    fixture.nativeElement.remove();
  });

  it('keyboard (prueba del 9-nov, segunda): focus on Anuncio, Cupo with thousands and Enter saves once', async () => {
    setup();
    const pending = new Subject<typeof saved>();
    assignSpy.mockReturnValue(pending);
    const fixture = TestBed.createComponent(QuotaAssignmentComponent);
    document.body.appendChild(fixture.nativeElement);
    fixture.detectChanges();
    await new Promise((resolve) => setTimeout(resolve));
    const host = fixture.nativeElement as HTMLElement;
    expect(document.activeElement?.getAttribute('data-field-key')).toBe('announcement');

    fixture.componentInstance.onAnnouncementChange(label);
    fixture.detectChanges();
    const quota = host.querySelector<HTMLInputElement>('[data-field-key=quota]')!;
    quota.focus();
    quota.value = '4000';
    quota.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(quota.value).toBe('4.000');

    const enter = () => quota.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true }));
    enter();
    enter();

    expect(assignSpy).toHaveBeenCalledTimes(1);
    expect(assignSpy).toHaveBeenCalledWith(32, { assignedQuota: 4000 });
    fixture.nativeElement.remove();
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
    expect(component.announcementOptions()).toEqual([label]);
    expect(component.saving()).toBe(false);
  });

  it('save without an announcement does nothing', () => {
    setup();
    component.save();
    expect(assignSpy).not.toHaveBeenCalled();
  });

  it('quota-assignment.json: Cupo lleva los puntos de miles al escribir', () => {
    const quota = [...quotaJson.leftColumn, ...quotaJson.rightColumn].find((f) => f.key === 'quota') as { liveThousands?: boolean };
    expect(quota.liveThousands).toBe(true);
  });
});
