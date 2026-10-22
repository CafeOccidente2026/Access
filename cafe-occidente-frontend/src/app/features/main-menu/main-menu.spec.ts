import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import menu from '../../../../public/assets/data/main-menu.json';
import { UserRole } from '../../core/models';
import { AgencyService } from '../../core/services/agency.service';
import { AuthService } from '../../core/services/auth.service';
import { ContentService } from '../../core/services/content.service';
import { MainMenuComponent } from './main-menu';

const ADMIN_ONLY = ['Registro de Control', 'Usuarios'];

describe('MainMenuComponent (HU-01, HU-06)', () => {
  let chooseSpy: ReturnType<typeof vi.spyOn>;

  beforeEach(() => localStorage.clear());

  function render(role: UserRole): HTMLElement {
    localStorage.setItem('cafeoccidente.token', 'jwt');
    localStorage.setItem('cafeoccidente.role', role);
    localStorage.setItem('cafeoccidente.agencyId', '1');
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ContentService, useValue: { loadJson: () => of(menu) } },
        { provide: AgencyService, useValue: { list: () => of([{ id: 1, name: 'Buesaco' }, { id: 4, name: 'El Tambo' }]) } },
      ],
    });
    chooseSpy = vi
      .spyOn(TestBed.inject(AuthService), 'chooseWorkingAgency')
      .mockReturnValue(of({ username: 'x', roleName: role, agencyId: 4, agencyName: 'El Tambo' }));
    const fixture = TestBed.createComponent(MainMenuComponent);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  const labels = (el: HTMLElement) => Array.from(el.querySelectorAll('app-button')).map((b) => b.textContent?.trim());

  it('CA-1: the Administrator sees every option, including the exclusive ones, and the Agencias button', () => {
    const el = render('ADMINISTRADOR');
    expect(labels(el)).toEqual(expect.arrayContaining([...ADMIN_ONLY, 'Actualizar Anuncios', 'Compras', 'Suministros', 'Vendedores']));
    expect(el.querySelector('[data-testid="agencies"]')).not.toBeNull();
  });

  it('CA-2: the User does not see the exclusive options nor Agencias, but sees Compras', () => {
    const el = render('USUARIO');
    expect(labels(el)).toContain('Compras');
    for (const exclusive of [...ADMIN_ONLY, 'Actualizar Anuncios', 'Exportar Informacion', 'Exportado Especial']) {
      expect(labels(el)).not.toContain(exclusive);
    }
    expect(el.querySelector('[data-testid="agencies"]')).toBeNull();
  });

  it('CA-3: Operaciones only sees Actualizar Anuncios (plus leaving the application)', () => {
    expect(labels(render('OPERACIONES'))).toEqual(['Actualizar Anuncios', 'Salir de la aplicación']);
  });

  it('RF-05 / Q22: Contabilizado is there, disabled and marked as pending, for Administrator and User', () => {
    const el = render('USUARIO');
    const button = Array.from(el.querySelectorAll('app-button')).find((b) => b.textContent?.trim() === 'Contabilizado');
    expect(button?.querySelector('button')?.disabled).toBe(true);
    expect(el.textContent).toContain('Pendiente de levantamiento');
  });

  it('HU-06 CA-1: choosing an agency in the Agencias button sends it to the server', () => {
    const el = render('ADMINISTRADOR');
    const select = el.querySelector('[data-testid="agencies"] select') as HTMLSelectElement;
    expect(select.value).toBe('1');

    select.value = '4';
    select.dispatchEvent(new Event('change'));

    expect(chooseSpy).toHaveBeenCalledWith(4);
  });
});
