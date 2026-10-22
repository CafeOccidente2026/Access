import { describe, expect, it } from 'vitest';

import futurePurchases from '../../../public/assets/data/future-purchases-menu.json';
import pettyCash from '../../../public/assets/data/petty-cash-menu.json';
import purchases from '../../../public/assets/data/purchases-menu.json';
import supplies from '../../../public/assets/data/supplies-menu.json';
import vendors from '../../../public/assets/data/vendors-menu.json';
import { MenuOption } from '../core/models';

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

describe('submenus (HU-02)', () => {
  it('CA-1 Compras', () => {
    expectInRfOrder(purchases, ['Genera Remesa', 'Compras Café Seco', 'Compras Café Verde', 'Compra Pasilla', 'Compras a Futuro',
      'Inventarios', 'Anular Documento', 'Generar Reporte Anuncio', 'Consulta compras por fecha', 'Consultar Compras']);
  });

  it('CA-1: Anular Documento (RN-23.1) and Generar Reporte Anuncio (RF-24) are only for the Administrator', () => {
    expect(option(purchases, 'Anular Documento')?.roles).toEqual(['ADMINISTRADOR']);
    expect(option(purchases, 'Generar Reporte Anuncio')?.roles).toEqual(['ADMINISTRADOR']);
  });

  it('CA-2 Compras a Futuro', () => {
    expectInRfOrder(futurePurchases, ['Asignar Cupo a Anuncios', 'Facturar Anuncios con Cupos', 'Anunciadas Hoy',
      'Ingresar Compras a Futuro', 'Facturar Compras Anunciadas', 'Generar Informe Anuncios a Futuro',
      'Generar Informe Saldo Anuncios', 'Volver al Panel de Control Compras']);
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
