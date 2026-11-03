import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';

import { UserRole } from '../../../core/models';
import { AgencyPickerComponent } from './agency-picker';

/** HU-06 CA-2 (RN-05.1.3): el campo Agencia muestra la agencia de trabajo sin poder editarla. */
describe('AgencyPickerComponent', () => {
  beforeEach(() => localStorage.clear());

  function render(role: UserRole): HTMLElement {
    localStorage.setItem('cafeoccidente.role', role);
    localStorage.setItem('cafeoccidente.agencyName', 'El Tambo');
    const fixture = TestBed.createComponent(AgencyPickerComponent);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('CA-2: the Administrator sees his working agency, read-only, like the User', () => {
    for (const role of ['ADMINISTRADOR', 'USUARIO'] as const) {
      TestBed.resetTestingModule();
      const input = render(role).querySelector('input') as HTMLInputElement;
      expect(input.readOnly).toBe(true);
      expect(input.value).toBe('El Tambo');
    }
  });

  it('gives its input the accessible name it receives (the visible label is outside the component)', () => {
    const fixture = TestBed.createComponent(AgencyPickerComponent);
    fixture.componentRef.setInput('ariaLabel', 'Agencia');
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('input')!.getAttribute('aria-label')).toBe('Agencia');
  });
});

