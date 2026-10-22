import { describe, expect, it } from 'vitest';

import { Route } from '@angular/router';
import futurePurchases from '../../../public/assets/data/future-purchases-menu.json';
import pettyCash from '../../../public/assets/data/petty-cash-menu.json';
import purchases from '../../../public/assets/data/purchases-menu.json';
import supplies from '../../../public/assets/data/supplies-menu.json';
import inventory from '../../../public/assets/data/inventory-menu.json';
import vendors from '../../../public/assets/data/vendors-menu.json';
import { routes } from '../app.routes';
import { DEFAULT_ROUTE_ROLES } from '../core/guards/auth.guard';
import { MenuOption, UserRole } from '../core/models';

/** Opciones del menu en el orden de pantalla (el JSON las guarda en ese orden, en una o varias columnas). */
function options(menu: unknown): MenuOption[] {
  if (Array.isArray(menu)) {
    return menu.flatMap(options);
  }
  if (menu && typeof menu === 'object') {
    return 'label' in menu ? [menu as MenuOption] : Object.values(menu).flatMap(options);
  }
  return [];
}

const normalize = (label: string) => label.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();

/** Las opciones del RF aparecen en el orden del RF; puede haber otras en medio (Q5: no se quitan todavia). */
function expectInRfOrder(menu: unknown, rf: string[]): void {
  const labels = options(menu).map((o) => normalize(o.label));
  let from = 0;
  for (const expected of rf) {
    const at = labels.indexOf(normalize(expected), from);
    expect(at, `"${expected}" en el orden del RF`).toBeGreaterThanOrEqual(0);
    from = at + 1;
  }
}

const option = (menu: unknown, label: string) => options(menu).find((o) => o.label === label);

/** Roles con que el guard deja entrar a una ruta (los de la ruta o, si no dice, los de pantalla de operacion). */
function routeRoles(path: string, list: Route[] = routes): readonly UserRole[] | undefined {
  for (const route of list) {
    if (route.path === path) {
      return (route.data?.['roles'] as readonly UserRole[] | undefined) ?? DEFAULT_ROUTE_ROLES;
    }
    const child = route.children && routeRoles(path, route.children);
    if (child) {
      return child;
    }
  }
  return undefined;
}

const ADMIN_USER = ['ADMINISTRADOR', 'USUARIO'];

describe('submenus (HU-02)', () => {
  it('CA-1 Compras', () => {
    expectInRfOrder(purchases, ['Genera Remesa', 'Compras Café Seco', 'Compras Café Verde', 'Compra Pasilla', 'Compras a Futuro',
      'Inventarios', 'Anular Documento', 'Generar Reporte Anuncio', 'Consulta compras por fecha', 'Consultar Compras']);
  });

  it('CA-1: Anular Documento (RN-23.1) is only for the Administrator', () => {
    expect(option(purchases, 'Anular Documento')?.roles).toEqual(['ADMINISTRADOR']);
    expect(routeRoles('compras/anular')).toEqual(['ADMINISTRADOR']);
  });

  it('Generar Reporte Anuncio is for Administrator and User (area de compras, 22 oct; prevails over RF-24)', () => {
    expect(option(purchases, 'Generar Reporte Anuncio')?.roles).toBeUndefined();
    expect(routeRoles('compras/reporte-anuncio')).toEqual(ADMIN_USER);
  });

  it('CA-2 Compras a Futuro', () => {
    expectInRfOrder(futurePurchases, ['Asignar Cupo a Anuncios', 'Facturar Anuncios con Cupos', 'Anunciadas Hoy',
      'Ingresar Compras a Futuro', 'Facturar Compras Anunciadas', 'Generar Informe Anuncios a Futuro',
      'Generar Informe Saldo Anuncios', 'Volver al Panel de Control Compras']);
  });

  it('Restaurar Anuncios con Cupos and Restaurar Compras Anunciadas are only for the Administrator', () => {
    expect(option(futurePurchases, 'Restaurar Anuncios con Cupos')?.roles).toEqual(['ADMINISTRADOR']);
    expect(option(futurePurchases, 'Restaurar Compras Anunciadas')?.roles).toEqual(['ADMINISTRADOR']);
  });

  it('Asignar Cupo a Anuncios (RF-21.1) is for Administrator and User, also in the route', () => {
    expect(option(futurePurchases, 'Asignar Cupo a Anuncios')?.roles).toBeUndefined();
    expect(routeRoles('compras/futuro/asignar-cupo')).toEqual(ADMIN_USER);
  });

  it('Ajustes Caja, Conductores, Inf Ventas Benef por Fechas and Saldos Cupos NESS are for Administrator and User', () => {
    const cases: [unknown, string, string][] = [
      [supplies, 'Ajustes Caja Caf�', 'suministros/ajustes-caja'],
      [inventory, 'Ingresar Conductores', 'compras/inventarios/conductores'],
      [vendors, 'Inf Ventas Benef por Fechas', 'vendedores/beneficiario-fechas'],
      [vendors, 'Consulta Saldos Cupos Ness', 'vendedores/saldos-cupos-ness'],
    ];
    for (const [menu, label, path] of cases) {
      expect(option(menu, label)?.roles, label).toBeUndefined();
      expect(routeRoles(path), path).toEqual(ADMIN_USER);
    }
  });

  it('CA-3 Suministros', () => {
    expectInRfOrder(supplies, ['Registrar Suministro Efectivo', 'Informe Caja', 'Relación Cheques', 'Rel Cheques Especial',
      'Rel Formas de Pago', 'Suministro Empaques', 'Préstamo Empaques', 'Informe Empaques', 'Ingresar Suministros',
      'Cheques Girados', 'Informe Suministros RP', 'Informe Suministros LF', 'Caja Menor']);
  });

  it('CA-4 Caja Menor', () => {
    expectInRfOrder(pettyCash, ['Ingresar Suministros Caja Menor', 'Registrar Gastos Caja Menor', 'Informe Caja Menor']);
  });

  it('CA-5 Vendedores, with Catalogar Terceros instead of Ingresar Vendedores', () => {
    expectInRfOrder(vendors, ['Catalogar Terceros', 'Genera Inf Ventas Benef', 'Consultar Asociados', 'Actualiza Cupos NESS',
      'Consulta Cupos Ness']);
    expect(option(vendors, 'Ingresar Vendedores')).toBeUndefined();
  });

  it('Actualiza Cupos NESS only shows up for the Administrator', () => {
    expect(option(vendors, 'Actualiza Cupos NESS')?.roles).toEqual(['ADMINISTRADOR']);
  });
});
