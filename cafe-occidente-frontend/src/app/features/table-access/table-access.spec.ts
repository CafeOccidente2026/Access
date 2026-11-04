import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import content from '../../../../public/assets/data/table-access.json';
import { routes } from '../../app.routes';
import { API_BASE_URL } from '../../core/config/api.config';
import { ContentService } from '../../core/services/content.service';
import { NavigationService } from '../../core/services/navigation.service';
import { PAGE_SIZE, TableAccessComponent, columnLabel } from './table-access';

const TABLES = ['CAJA_MENOR', 'COMPRAS_CAFE_SECO', 'COMPRAS_CAFE_VERDE', 'COMPRAS_PASILLA', 'COMPRAS_A_FUTURO',
  'INVENTARIO', 'PRODUCTOS', 'EMPAQUE', 'SUMINISTROS', 'CAJA'];

describe('TableAccessComponent (HU-04)', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.setItem('cafeoccidente.agencyName', 'El Tambo');
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ContentService, useValue: { loadJson: () => of(content) } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  function render() {
    const fixture = TestBed.createComponent(TableAccessComponent);
    fixture.detectChanges();
    http.expectOne(`${API_BASE_URL}/tables`).flush(TABLES);
    fixture.detectChanges();
    return { fixture, component: fixture.componentInstance, el: fixture.nativeElement as HTMLElement };
  }

  const buttons = (el: HTMLElement) => Array.from(el.querySelectorAll('app-button')).map((b) => b.textContent?.trim());

  it('CA-1: lists the tables available for consultation, like the Tablas form of Access', () => {
    const { el } = render();
    expect(buttons(el)).toEqual(['Caja Menor', 'Compras Café Seco', 'Compras Café Verde', 'Compras Pasilla',
      'Compras a Futuro', 'Inventario', 'Productos', 'Empaque', 'Suministros', 'Caja', 'Cerrar']);
  });

  it('CA-2: shows the rows of the working agency, read only, with readable headers', () => {
    const { fixture, component, el } = render();
    component.open('COMPRAS_CAFE_SECO');
    http.expectOne(`${API_BASE_URL}/tables/COMPRAS_CAFE_SECO?page=0&size=${PAGE_SIZE}`)
      .flush({ columns: ['id', 'numero_factura'], rows: [['2', '43388'], ['1', null]], total: 2 });
    fixture.detectChanges();

    const sheet = el.querySelector('[data-testid="sheet"]')!;
    expect(Array.from(sheet.querySelectorAll('th')).map((th) => th.textContent?.trim())).toEqual(['Id', 'Numero factura']);
    expect(sheet.querySelectorAll('tbody tr')).toHaveLength(2);
    expect(sheet.querySelectorAll('input, textarea, select')).toHaveLength(0);
    expect(el.textContent).toContain('Agencia: El Tambo');
  });

  it('pages through a long table and goes back to the list', () => {
    const { fixture, component, el } = render();
    component.open('CAJA');
    http.expectOne(`${API_BASE_URL}/tables/CAJA?page=0&size=${PAGE_SIZE}`).flush({ columns: ['id'], rows: [['1']], total: 150 });
    fixture.detectChanges();
    expect(buttons(el)).toContain('Siguiente');

    component.open('CAJA', 1);
    http.expectOne(`${API_BASE_URL}/tables/CAJA?page=1&size=${PAGE_SIZE}`).flush({ columns: ['id'], rows: [['1']], total: 150 });
    fixture.detectChanges();
    expect(buttons(el)).toContain('Anterior');
    expect(buttons(el)).not.toContain('Siguiente');

    component.back();
    fixture.detectChanges();
    expect(buttons(el)).toContain('Caja Menor');
  });

  it('Productos is common to all agencies; an empty table and an error say so', () => {
    const { fixture, component, el } = render();
    component.open('PRODUCTOS');
    http.expectOne(`${API_BASE_URL}/tables/PRODUCTOS?page=0&size=${PAGE_SIZE}`).flush({ columns: ['id'], rows: [], total: 0 });
    fixture.detectChanges();
    expect(el.textContent).toContain('Común a todas las agencias');
    expect(el.textContent).toContain('La tabla no tiene registros para esta agencia.');

    component.open('EMPAQUE');
    http.expectOne(`${API_BASE_URL}/tables/EMPAQUE?page=0&size=${PAGE_SIZE}`).flush(null, { status: 500, statusText: 'Error' });
    fixture.detectChanges();
    expect(component.error()).toBe(true);
  });

  it('Cerrar goes back to Acceso Principal; the route is for the Administrator or with the screen granted', () => {
    const { component } = render();
    const goTo = vi.spyOn(TestBed.inject(NavigationService), 'goTo').mockImplementation(() => undefined);
    component.close();
    expect(goTo).toHaveBeenCalledWith('/acceso-principal');
    const route = routes.find((r) => r.children?.some((c) => c.path === 'acceso-tablas'))
      ?.children?.find((c) => c.path === 'acceso-tablas');
    expect(route?.data).toEqual({ roles: ['ADMINISTRADOR'], screens: ['ACCESO_TABLAS'] });
  });

  it('turns column names into headers', () => {
    expect(columnLabel('fecha_compra')).toBe('Fecha compra');
    expect(columnLabel('id')).toBe('Id');
  });
});
