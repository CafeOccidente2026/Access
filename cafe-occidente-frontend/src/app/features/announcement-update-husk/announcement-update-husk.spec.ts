import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import content from '../../../../public/assets/data/announcement-update-husk.json';
import { SUCCESS_MESSAGE_DURATION_MS } from '../../shared/ui';
import { AnnouncementUpdateHuskComponent } from './announcement-update-husk';
import { AnnouncementService } from '../../core/services/announcement.service';
import { ContentService } from '../../core/services/content.service';

describe('AnnouncementUpdateHuskComponent - validación numérica en vivo', () => {
  const page = {
    windowTitle: 'ACTUALIZAR ANUNCIO PASILLA',
    sectionTitle: 'Actualizar precios',
    basePriceLoadLabel: 'Pr Base Carg Perg Seco',
    pointPriceLabel: 'Pr Por Punto',
    updateButton: 'Actualizar',
    successMessage: 'Anuncio actualizado. Número asignado:',
    errorMessage: 'Ocurrió un error. Verifique los datos e intente de nuevo.',
  };

  let createHuskSpy: ReturnType<typeof vi.fn>;
  let component: AnnouncementUpdateHuskComponent;

  function setup() {
    createHuskSpy = vi.fn(() => of({ announcementNumber: 'SDBU-8' }));
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ContentService, useValue: { loadJson: () => of(page) } },
        { provide: AnnouncementService, useValue: { createHusk: createHuskSpy } },
      ],
    });
    component = TestBed.createComponent(AnnouncementUpdateHuskComponent).componentInstance;
  }

  it('flags letters with the specific message and keeps showing what was typed', () => {
    setup();
    component.onNumericInput('basePriceLoad', '12a3');

    expect(component.fieldErrors.basePriceLoad).toBe('No se aceptan letras, solo números');
    expect(component.numeric.basePriceLoad).toBe('12a3');
  });

  it('flags a negative value with the specific message', () => {
    setup();
    component.onNumericInput('pointPrice', '-8908');

    expect(component.fieldErrors.pointPrice).toBe('No se aceptan valores negativos en este campo');
  });

  it('does not submit while any field still has an invalid value', () => {
    setup();
    component.onNumericInput('basePriceLoad', '1200000');
    component.onNumericInput('pointPrice', 'abc');

    component.update();

    expect(createHuskSpy).not.toHaveBeenCalled();
  });

  it('shows the real backend error message instead of the generic one on a failed update', () => {
    setup();
    createHuskSpy.mockReturnValue(throwError(() => ({ error: { message: 'El precio no puede ser negativo' } })));
    component.onNumericInput('basePriceLoad', '1200000');
    component.onNumericInput('pointPrice', '8908');

    component.update();

    expect(component.error()).toBe('El precio no puede ser negativo');
  });
});

describe('AnnouncementUpdateHuskComponent (HU-12)', () => {
  let createHusk: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    vi.useFakeTimers();
    createHusk = vi.fn(() => of({ announcementNumber: 'SDBU-8' }));
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ContentService, useValue: { loadJson: () => of(content) } },
        { provide: AnnouncementService, useValue: { createHusk } },
      ],
    });
  });

  afterEach(() => vi.useRealTimers());

  function render() {
    const fixture = TestBed.createComponent(AnnouncementUpdateHuskComponent);
    fixture.detectChanges();
    vi.runOnlyPendingTimers();
    return { fixture, component: fixture.componentInstance, el: fixture.nativeElement as HTMLElement };
  }

  const field = (el: HTMLElement, key: string) => el.querySelector(`[data-field-key="${key}"]`);

  it('RN-09.1: the cursor starts on the first field and Enter only moves on when it is filled', () => {
    const { component, el } = render();
    expect(document.activeElement).toBe(field(el, 'basePriceLoad'));

    component.onEnter('basePriceLoad');
    vi.runOnlyPendingTimers();
    expect(document.activeElement).toBe(field(el, 'basePriceLoad'));

    component.onNumericInput('basePriceLoad', '1200000');
    component.onEnter('basePriceLoad');
    vi.runOnlyPendingTimers();
    expect(document.activeElement).toBe(field(el, 'pointPrice'));

    component.onEnter('pointPrice'); // vacio: no publica
    expect(createHusk).not.toHaveBeenCalled();
  });

  it('RN-09.1: the mouse cannot skip an empty first field', () => {
    const { component, el } = render();
    component.onFocus('pointPrice');
    vi.runOnlyPendingTimers();
    expect(document.activeElement).toBe(field(el, 'basePriceLoad'));
  });

  it('CA-1: publishes and shows "Anuncio actualizado" for 3 seconds', () => {
    const { fixture, component, el } = render();
    component.onNumericInput('basePriceLoad', '1200000');
    component.onNumericInput('pointPrice', '9600');
    component.onEnter('pointPrice');
    expect(createHusk).toHaveBeenCalledWith({ basePriceLoad: 1200000, pointPrice: 9600 });

    fixture.detectChanges();
    expect(el.querySelector('[data-testid="success-overlay"]')?.textContent?.trim()).toBe('Anuncio actualizado');
    vi.advanceTimersByTime(SUCCESS_MESSAGE_DURATION_MS - 1);
    expect(component.message()).toBe('Anuncio actualizado');
    vi.advanceTimersByTime(1);
    expect(component.message()).toBeNull();
  });
});
