import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it } from 'vitest';

import menu from '../../../../public/assets/data/purchases-menu.json';
import { Screen, UserRole } from '../../core/models';
import { ContentService } from '../../core/services/content.service';
import { PurchasesMenuComponent } from './purchases-menu';

describe('PurchasesMenuComponent', () => {
  beforeEach(() => localStorage.clear());

  function labels(role: UserRole, screens: readonly Screen[] = []): (string | undefined)[] {
    localStorage.setItem('cafeoccidente.token', 'jwt');
    localStorage.setItem('cafeoccidente.role', role);
    localStorage.setItem('cafeoccidente.screens', JSON.stringify(screens));
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ContentService, useValue: { loadJson: () => of(menu) } },
      ],
    });
    const fixture = TestBed.createComponent(PurchasesMenuComponent);
    fixture.detectChanges();
    return Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('app-button')).map((b) => b.textContent?.trim());
  }

  it('RN-23.1: Anular Documento is only for the Administrator', () => {
    expect(labels('USUARIO')).not.toContain('Anular Documento');
    TestBed.resetTestingModule();
    expect(labels('ADMINISTRADOR')).toContain('Anular Documento');
  });

  it('HU-01 CA-4: with Anular Documento granted, the User sees it next to their usual options', () => {
    expect(labels('USUARIO', ['ANULAR_DOCUMENTO'])).toEqual(expect.arrayContaining(['Compras Café Seco', 'Anular Documento']));
  });

  it('C8: Operaciones with Anular Documento only sees that option (and the way back)', () => {
    expect(labels('OPERACIONES', ['ANULAR_DOCUMENTO'])).toEqual(['Anular Documento', 'Volver al Pánel de Control Principal']);
  });
});
