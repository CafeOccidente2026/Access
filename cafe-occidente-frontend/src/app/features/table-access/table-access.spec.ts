import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import content from '../../../../public/assets/data/table-access.json';
import { API_BASE_URL } from '../../core/config/api.config';
import { TableInfo } from '../../core/models/table-access.model';
import { ContentService } from '../../core/services/content.service';
import { NavigationService } from '../../core/services/navigation.service';
import { CHUNK, DEBOUNCE_MS, TableAccessComponent } from './table-access';

const EMPAQUES: TableInfo = {
  name: 'empaques', byAgency: true, primaryKey: 'id',
  columns: [
    { name: 'id', kind: 'NUMBER' }, { name: 'id_transaccion', kind: 'TEXT' },
    { name: 'fecha', kind: 'DATE' }, { name: 'entradas', kind: 'NUMBER' },
  ],
};
const PRODUCTOS: TableInfo = { name: 'productos', byAgency: false, primaryKey: 'id', columns: [{ name: 'id', kind: 'NUMBER' }] };
const NUEVA: TableInfo = { name: 'tabla_nueva', byAgency: false, primaryKey: 'id', columns: [{ name: 'id', kind: 'NUMBER' }] };

/** C8 (prueba del 11-nov, punto 6): Acceso a Tablas como abrir una tabla en Access (vista hoja de datos). */
describe('TableAccessComponent (HU-04, C8)', () => {
  let http: HttpTestingController;

  function setup(role: 'ADMINISTRADOR' | 'USUARIO') {
    localStorage.setItem('cafeoccidente.role', role);
    localStorage.setItem('cafeoccidente.agencyId', '1');
    localStorage.setItem('cafeoccidente.agencyName', 'Buesaco 1');
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ContentService, useValue: { loadJson: () => of(content) } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(TableAccessComponent);
    fixture.detectChanges();
    http.expectOne(`${API_BASE_URL}/tables`).flush([EMPAQUES, PRODUCTOS, NUEVA]);
    if (role === 'ADMINISTRADOR') {
      http.expectOne(`${API_BASE_URL}/agencies`).flush([{ id: 1, name: 'Buesaco 1' }, { id: 4, name: 'El Tambo' }]);
    }
    fixture.detectChanges();
    return { fixture, component: fixture.componentInstance, el: fixture.nativeElement as HTMLElement };
  }

  beforeEach(() => vi.useFakeTimers());
  afterEach(() => {
    vi.useRealTimers();
    localStorage.clear();
  });

  const rowsRequest = () => http.expectOne((r) => r.url === `${API_BASE_URL}/tables/empaques`);
  const params = (req: TestRequest) => Object.fromEntries(req.request.params.keys().map((k) => [k, req.request.params.get(k)]));
  const page = (from: number, count: number, total: number) => ({
    columns: ['id', 'id_transaccion', 'fecha', 'entradas'],
    rows: Array.from({ length: count }, (_, i) => [String(from + i + 1), 'T' + (from + i + 1), '2026-01-01', '5']),
    total,
  });

  it('lists the tables the server allows, with their name from the JSON or the real name', () => {
    const { el } = setup('USUARIO');
    const labels = Array.from(el.querySelectorAll('app-button')).map((b) => b.textContent?.trim());
    expect(labels).toEqual(['Empaque', 'Productos', 'tabla_nueva', 'Cerrar']);
  });

  it('opens the sheet with every column and its real name, and counts "Registro X de N" like Access', () => {
    const { fixture, component, el } = setup('USUARIO');
    component.open(EMPAQUES);
    const req = rowsRequest();
    expect(params(req)).toEqual({ offset: '0', limit: String(CHUNK) });
    req.flush(page(0, 3, 250));
    fixture.detectChanges();

    expect(Array.from(el.querySelectorAll('[data-testid="header"]')).map((h) => h.textContent?.trim().replace(/[▲▼]/g, '').trim()))
      .toEqual(['id', 'id_transaccion', 'fecha', 'entradas']);
    expect(component.counter()).toBe('Registro 1 de 250');
    // Solo lectura: en la hoja no hay donde escribir, salvo los filtros y la busqueda.
    expect(el.querySelectorAll('[data-testid="cell"] input, [data-testid="cell"] textarea')).toHaveLength(0);
  });

  it('loads the next stretch of rows when the scroll gets near the end, not pages', () => {
    const { component } = setup('USUARIO');
    component.open(EMPAQUES);
    rowsRequest().flush(page(0, CHUNK, 250));

    component.onScrolled(10);
    http.expectNone((r) => r.url.endsWith('/tables/empaques'));
    component.onScrolled(CHUNK - 20);
    const next = rowsRequest();
    expect(params(next)).toEqual({ offset: String(CHUNK), limit: String(CHUNK) });
    // Mientras carga no pide otra vez.
    component.onScrolled(CHUNK - 10);
    next.flush(page(CHUNK, CHUNK, 250));
    expect(component.rows()).toHaveLength(2 * CHUNK);

    component.onScrolled(2 * CHUNK - 5);
    rowsRequest().flush(page(2 * CHUNK, 50, 250));
    component.onScrolled(249);
    http.expectNone((r) => r.url.endsWith('/tables/empaques'));
  });

  it('sorting, filters and search run in the server; typing waits a moment and asks once', () => {
    const { component } = setup('USUARIO');
    component.open(EMPAQUES);
    rowsRequest().flush(page(0, 3, 3));

    component.toggleSort('entradas');
    expect(params(rowsRequest())).toMatchObject({ sort: 'entradas', dir: 'asc', offset: '0' });
    component.toggleSort('entradas');
    expect(params(rowsRequest())).toMatchObject({ sort: 'entradas', dir: 'desc' });
    component.toggleSort('entradas');
    expect(params(rowsRequest())).not.toHaveProperty('sort');

    component.setFilter('id_transaccion', 'T');
    component.setFilter('id_transaccion', 'T1');
    component.setFilter('entradas.from', '5');
    component.setFilter('fecha.to', '2026-12-31');
    component.setSearch('cafe');
    http.expectNone((r) => r.url.endsWith('/tables/empaques'));
    vi.advanceTimersByTime(DEBOUNCE_MS);
    expect(params(rowsRequest())).toMatchObject({
      'f.id_transaccion': 'T1', 'f.entradas.from': '5', 'f.fecha.to': '2026-12-31', q: 'cafe', offset: '0',
    });

    component.setFilter('id_transaccion', '');
    vi.advanceTimersByTime(DEBOUNCE_MS);
    expect(params(rowsRequest())).not.toHaveProperty('f.id_transaccion');
  });

  it('the Administrator sees the working agency by default and can choose another or "Todas"', () => {
    const { fixture, component, el } = setup('ADMINISTRADOR');
    component.open(EMPAQUES);
    expect(params(rowsRequest())).toMatchObject({ agency: '1' });
    fixture.detectChanges();
    const options = Array.from(el.querySelectorAll('[data-testid="agency"] option')).map((o) => o.textContent?.trim());
    expect(options).toEqual(['Todas', 'Buesaco 1', 'El Tambo']);

    component.setAgency('ALL');
    expect(params(rowsRequest())).toMatchObject({ agency: 'ALL' });
    component.setAgency('4');
    expect(params(rowsRequest())).toMatchObject({ agency: '4' });

    // Una tabla sin agencia se ve completa: no hay selector.
    component.open(PRODUCTOS);
    http.expectOne((r) => r.url.endsWith('/tables/productos')).flush({ columns: ['id'], rows: [], total: 0 });
    fixture.detectChanges();
    expect(el.querySelector('[data-testid="agency"]')).toBeNull();
  });

  it('a User with the permission has no agency selector and never sends one', () => {
    const { fixture, component, el } = setup('USUARIO');
    component.open(EMPAQUES);
    expect(params(rowsRequest())).not.toHaveProperty('agency');
    fixture.detectChanges();
    expect(el.querySelector('[data-testid="agency"]')).toBeNull();
  });

  it('moves through the records with the keyboard like Access, and Esc goes back to the tables', () => {
    const { component } = setup('USUARIO');
    component.open(EMPAQUES);
    rowsRequest().flush(page(0, 30, 30));
    const key = (k: string) => component.onKey(new KeyboardEvent('keydown', { key: k }));

    key('ArrowDown');
    key('ArrowDown');
    expect(component.counter()).toBe('Registro 3 de 30');
    key('ArrowUp');
    expect(component.counter()).toBe('Registro 2 de 30');
    key('End');
    expect(component.counter()).toBe('Registro 30 de 30');
    key('PageUp');
    expect(component.counter()).toBe('Registro 10 de 30');
    key('Home');
    expect(component.counter()).toBe('Registro 1 de 30');
    key('ArrowUp');
    expect(component.counter()).toBe('Registro 1 de 30');

    key('Escape');
    expect(component.table()).toBeNull();
  });

  it('with no rows the counter says 0 and shows the empty message; an error shows the reason of the server', () => {
    const { fixture, component, el } = setup('USUARIO');
    component.open(EMPAQUES);
    rowsRequest().flush(page(0, 0, 0));
    fixture.detectChanges();
    expect(component.counter()).toBe('Registro 0 de 0');
    expect(el.textContent).toContain(content.emptyMessage);

    component.setSearch('x');
    vi.advanceTimersByTime(DEBOUNCE_MS);
    rowsRequest().flush({ message: 'Valor no valido para fecha: x' }, { status: 400, statusText: 'Bad Request' });
    expect(component.error()).toBe('Valor no valido para fecha: x');
  });

  it('Cerrar goes back to the main access menu', () => {
    const { component } = setup('USUARIO');
    const goTo = vi.spyOn(TestBed.inject(NavigationService), 'goTo').mockImplementation(() => undefined);
    component.close();
    expect(goTo).toHaveBeenCalledWith('/acceso-principal');
  });
});
