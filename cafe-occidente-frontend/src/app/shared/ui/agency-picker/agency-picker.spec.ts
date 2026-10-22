import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { UserRole } from '../../../core/models';
import { AgencyService } from '../../../core/services/agency.service';
import { AgencyPickerComponent } from './agency-picker';

/** HU-06 CA-2 (RN-05.1.3): el campo Agencia muestra la agencia de trabajo sin poder editarla. */
describe('AgencyPickerComponent', () => {
  let list: ReturnType<typeof vi.fn>;

  beforeEach(() => localStorage.clear());

  function render(role: UserRole, selectable = false): HTMLElement {
    localStorage.setItem('cafeoccidente.role', role);
    localStorage.setItem('cafeoccidente.agencyName', 'El Tambo');
    list = vi.fn(() => of([{ id: 4, name: 'El Tambo' }]));
    TestBed.configureTestingModule({ providers: [{ provide: AgencyService, useValue: { list } }] });
    const fixture = TestBed.createComponent(AgencyPickerComponent);
    fixture.componentRef.setInput('selectable', selectable);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('CA-2: the Administrator sees his working agency, read-only, like the User', () => {
    for (const role of ['ADMINISTRADOR', 'USUARIO'] as const) {
      TestBed.resetTestingModule();
      const input = render(role).querySelector('input') as HTMLInputElement;
      expect(input.readOnly).toBe(true);
      expect(input.value).toBe('El Tambo');
      expect(list).not.toHaveBeenCalled();
    }
  });

  it('only a selectable picker (Exportar Informacion) lets the Administrator choose', () => {
    expect(render('ADMINISTRADOR', true).querySelector('app-combobox')).not.toBeNull();
    expect(list).toHaveBeenCalled();
  });
});
