import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import content from '../../../../public/assets/data/announcement-update.json';
import { AnnouncementService } from '../../core/services/announcement.service';
import { ContentService } from '../../core/services/content.service';
import { SUCCESS_MESSAGE_DURATION_MS } from '../../shared/ui';
import { AnnouncementUpdateComponent } from './announcement-update';

const TYPES = [
  { specialType: 'RN', basePriceLoad: 1546900, specialSurcharge: 80000, defectiveUnitPrice: 6000, announcementDate: '2026-10-26' },
  { specialType: 'ILLY', basePriceLoad: null, specialSurcharge: null, defectiveUnitPrice: null, announcementDate: null },
];

describe('AnnouncementUpdateComponent (HU-10, HU-11)', () => {
  let service: { funds: ReturnType<typeof vi.fn>; publishAll: ReturnType<typeof vi.fn>; types: ReturnType<typeof vi.fn>; publishType: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    vi.useFakeTimers();
    service = {
      funds: vi.fn(() => of([{ id: 1, code: 'RP' }, { id: 2, code: 'LF' }])),
      publishAll: vi.fn(() => of([{ announcementNumber: 'SDBU-33' }])),
      types: vi.fn(() => of(TYPES)),
      publishType: vi.fn(() => of({ announcementNumber: 'SDBU-34' })),
    };
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ContentService, useValue: { loadJson: () => of(content) } },
        { provide: AnnouncementService, useValue: service },
      ],
    });
  });

  afterEach(() => vi.useRealTimers());

  function render() {
    const fixture = TestBed.createComponent(AnnouncementUpdateComponent);
    fixture.detectChanges();
    vi.runOnlyPendingTimers();
    return { fixture, component: fixture.componentInstance, el: fixture.nativeElement as HTMLElement };
  }

  const field = (el: HTMLElement, key: string) => el.querySelector(`[data-field-key="${key}"]`) as HTMLElement;

  it('RN-08.1: the cursor starts on Fondo, and Enter only moves on when the field is filled', () => {
    const { component, el } = render();
    expect(document.activeElement).toBe(field(el, 'fundId'));

    component.onEnter('fundId');
    vi.runOnlyPendingTimers();
    expect(document.activeElement).toBe(field(el, 'fundId'));

    component.fundId = 1;
    component.onEnter('fundId');
    vi.runOnlyPendingTimers();
    expect(document.activeElement).toBe(field(el, 'basePriceLoad'));

    component.onEnter('basePriceLoad'); // vacio: no guarda
    expect(service.publishAll).not.toHaveBeenCalled();
  });

  it('RN-08.1: the mouse cannot skip an empty Fondo', () => {
    const { component, el } = render();
    component.onFocus('basePriceLoad');
    vi.runOnlyPendingTimers();
    expect(document.activeElement).toBe(field(el, 'fundId'));
  });

  it('HU-10 CA-1 and CA-2: one price for every type, then "Anuncio actualizado" for 3 seconds', () => {
    const { fixture, component, el } = render();
    component.fundId = 1;
    component.onPriceInput('1546900');
    expect(component.basePriceLoad).toBe('1.546.900');

    component.onEnter('basePriceLoad');
    expect(service.publishAll).toHaveBeenCalledWith({ fundId: 1, basePriceLoad: 1546900 });

    fixture.detectChanges();
    expect(el.querySelector('[data-testid="success-overlay"]')?.textContent).toContain('Anuncio actualizado');
    vi.advanceTimersByTime(SUCCESS_MESSAGE_DURATION_MS - 1);
    expect(component.message()).toBe('Anuncio actualizado');
    vi.advanceTimersByTime(1);
    expect(component.message()).toBeNull();
    expect(SUCCESS_MESSAGE_DURATION_MS).toBe(3000);
  });

  it('HU-11 CA-1: Actualizar lists each type with its price, bonus and pasilla price', () => {
    const { fixture, component, el } = render();
    component.openList();
    expect(service.types).not.toHaveBeenCalled(); // sin Fondo no hay listado

    component.fundId = 1;
    component.openList();
    fixture.detectChanges();
    expect(service.types).toHaveBeenCalledWith(1);
    const cells = Array.from(el.querySelectorAll('[data-testid="types"] tbody tr')).map((tr) =>
      Array.from(tr.querySelectorAll('input')).map((i) => i.value),
    );
    expect(el.querySelector('[data-testid="types"]')?.textContent).toContain('RN');
    expect(cells[0]).toEqual(['1.546.900', '80.000', '6.000']);
    expect(cells[1]).toEqual(['', '0', '0']);

    component.onFundChange();
    expect(component.rows()).toBeNull();
  });

  it('HU-11 CA-2: saving a row sends only that type; an unchanged price takes the price of the day', () => {
    const { component } = render();
    component.fundId = 1;
    component.openList();
    const rn = component.rows()![0];

    component.onRowInput(rn, 'specialSurcharge', '100000');
    component.saveRow(rn);
    expect(service.publishType).toHaveBeenLastCalledWith({
      fundId: 1, specialType: 'RN', basePriceLoad: null, specialSurcharge: 100000, defectiveUnitPrice: 6000,
    });

    component.onPriceInput('1600000'); // precio del dia digitado arriba
    component.saveRow(component.rows()![0]);
    expect(service.publishType).toHaveBeenLastCalledWith(expect.objectContaining({ basePriceLoad: 1600000 }));

    const changed = component.rows()![0];
    component.onRowInput(changed, 'basePriceLoad', '1700000');
    component.saveRow(changed);
    expect(service.publishType).toHaveBeenLastCalledWith(expect.objectContaining({ basePriceLoad: 1700000 }));
    expect(component.message()).toBe('Anuncio actualizado');
  });

  it('shows the message of the server when it rejects the request', () => {
    service.publishAll.mockReturnValue(throwError(() => ({ error: { message: 'Fondo no encontrado' } })));
    service.types.mockReturnValue(throwError(() => ({})));
    const { component } = render();
    component.fundId = 9;
    component.onPriceInput('1000');
    component.save();
    expect(component.error()).toBe('Fondo no encontrado');

    component.openList();
    expect(component.error()).toBe('Ocurrió un error. Verifique los datos e intente de nuevo.');

    component.onPriceInput('abc');
    expect(component.priceError).not.toBeNull();
    component.save();
    expect(service.publishAll).toHaveBeenCalledTimes(1);
  });
});
