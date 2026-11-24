import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it } from 'vitest';

import supplies from '../../../../public/assets/data/supplies-menu.json';
import vendors from '../../../../public/assets/data/vendors-menu.json';
import { ContentService } from '../../core/services/content.service';
import { SuppliesMenuComponent } from './supplies-menu';

describe('SuppliesMenuComponent', () => {
  beforeEach(() => {
    localStorage.clear();
    localStorage.setItem('cafeoccidente.token', 'jwt');
    localStorage.setItem('cafeoccidente.role', 'ADMINISTRADOR');
  });

  /** Etiquetas de cada columna de botones, en orden. */
  function columns(content: unknown): string[][] {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ContentService, useValue: { loadJson: () => of(content) } },
      ],
    });
    const fixture = TestBed.createComponent(SuppliesMenuComponent);
    fixture.detectChanges();
    return Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('[data-testid="menu-column"]')).map((column) =>
      Array.from(column.querySelectorAll('app-button')).map((b) => b.textContent?.trim() ?? ''),
    );
  }

  it('Suministros: Volver closes the second column, so nothing goes below the 10 buttons (no scroll at 1366x768)', () => {
    const [first, second] = columns(supplies);
    expect(first).toHaveLength(10);
    expect(second.at(-1)).toBe('Volver al Pánel de Control Principal');
    expect(second.length).toBeLessThan(first.length);
  });

  it('Vendedores (one column): Volver still closes the menu', () => {
    const [only] = columns(vendors);
    expect(only.at(-1)).toBe('Volver al Pánel de Control Principal');
  });
});
