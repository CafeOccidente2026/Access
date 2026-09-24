import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';

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
