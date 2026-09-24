import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';

import { AnnouncementUpdateComponent } from './announcement-update';
import { AnnouncementService } from '../../core/services/announcement.service';
import { ContentService } from '../../core/services/content.service';

/**
 * Antes de este fix, escribir letras o un negativo en estos campos los borraba en silencio (el
 * [value] de un solo sentido volvia a mostrar '' apenas Angular re-renderizaba) - sin ningun aviso
 * al usuario de por que. Ahora el campo muestra el error puntual y deja ver lo que se escribio.
 */
describe('AnnouncementUpdateComponent - validación numérica en vivo', () => {
  const page = {
    windowTitle: 'ACTUALIZAR ANUNCIO CON FACTOR',
    sectionTitle: 'Actualizar precios',
    basePriceLoadLabel: 'Pr Base Carg Perg Seco',
    defectiveUnitPriceLabel: 'Precio Pasilla',
    specialSurchargeLabel: 'SobrePr Carga Esp',
    specialTypeLabel: 'Especial',
    fundLabel: 'Fondo',
    updateButton: 'Actualizar',
    successMessage: 'Anuncio actualizado. Número asignado:',
    errorMessage: 'Ocurrió un error. Verifique los datos e intente de nuevo.',
    specialOptions: ['RN', 'ILLY'],
  };

  let createSpy: ReturnType<typeof vi.fn>;
  let component: AnnouncementUpdateComponent;

  function setup() {
    createSpy = vi.fn(() => of({ announcementNumber: 'SDBU-33' }));
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ContentService, useValue: { loadJson: () => of(page) } },
        {
          provide: AnnouncementService,
          useValue: { funds: () => of([{ id: 1, code: 'RP', name: 'Resolución Propia' }]), create: createSpy },
        },
      ],
    });
    component = TestBed.createComponent(AnnouncementUpdateComponent).componentInstance;
  }

  it('flags letters with the specific message and keeps showing what was typed', () => {
    setup();
    component.onNumericInput('basePriceLoad', '12a3');

    expect(component.fieldErrors.basePriceLoad).toBe('No se aceptan letras, solo números');
    expect(component.numeric.basePriceLoad).toBe('12a3');
  });

  it('flags a negative value with the specific message', () => {
    setup();
    component.onNumericInput('specialSurcharge', '-500');

    expect(component.fieldErrors.specialSurcharge).toBe('No se aceptan valores negativos en este campo');
    expect(component.numeric.specialSurcharge).toBe('-500');
  });

  it('clears the error and formats with thousands dots once the value becomes valid', () => {
    setup();
    component.onNumericInput('defectiveUnitPrice', 'abc');
    expect(component.fieldErrors.defectiveUnitPrice).not.toBeNull();

    component.onNumericInput('defectiveUnitPrice', '4000');

    expect(component.fieldErrors.defectiveUnitPrice).toBeNull();
    expect(component.numeric.defectiveUnitPrice).toBe('4.000');
  });

  it('does not submit while any field still has an invalid value', () => {
    setup();
    component.onNumericInput('basePriceLoad', '1200000');
    component.onNumericInput('specialSurcharge', 'abc');
    component.onNumericInput('defectiveUnitPrice', '4000');
    component.specialType = 'RN';
    component.fundId = 1;

    component.update();

    expect(createSpy).not.toHaveBeenCalled();
  });

  it('shows the real backend error message instead of the generic one on a failed update', () => {
    setup();
    createSpy.mockReturnValue(throwError(() => ({ error: { message: 'Ya existe un anuncio activo con estos datos' } })));
    component.onNumericInput('basePriceLoad', '1200000');
    component.onNumericInput('specialSurcharge', '5000');
    component.onNumericInput('defectiveUnitPrice', '4000');
    component.specialType = 'RN';
    component.fundId = 1;

    component.update();

    expect(component.error()).toBe('Ya existe un anuncio activo con estos datos');
  });
});
