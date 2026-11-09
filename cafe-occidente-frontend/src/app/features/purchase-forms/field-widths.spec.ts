import { describe, expect, it } from 'vitest';

import dry from '../../../../public/assets/data/purchase-form-dry.json';
import future from '../../../../public/assets/data/purchase-form-future.json';
import green from '../../../../public/assets/data/purchase-form-green.json';
import husk from '../../../../public/assets/data/purchase-form-husk.json';
import billing from '../../../../public/assets/data/purchase-form-quota-billing.json';
import quota from '../../../../public/assets/data/purchase-form-quota-purchase.json';
import { FormFieldDefinition } from '../../core/models';

type Field = FormFieldDefinition & { rowWidthClass?: string };

function fields(content: object): Field[] {
  return Object.values(content)
    .flatMap((v) => (Array.isArray(v) ? v : v && typeof v === 'object' && 'key' in v ? [v] : []))
    .filter((f): f is Field => typeof f === 'object' && f !== null && 'key' in f && 'type' in f);
}

/** px de una clase "min-w-[NNNpx]" o "max-w-[NNNpx]"; undefined si no esta. */
function px(classes: string | undefined, kind: 'min-w' | 'max-w'): number | undefined {
  const match = new RegExp(`${kind}-\\[(\\d+)px\\]`).exec(classes ?? '');
  return match ? Number(match[1]) : undefined;
}

/**
 * Ajustes de la prueba del 9-nov (punto 4): fechas, nombres, apellidos, direccion y Especial se ven completos en las
 * 6 pantallas de compra. Anchos: fecha dd/mm/aaaa con su icono; Especial el mas largo ("NESPRESSO LATE HARVEST CON
 * TAZA - FTUSA"); nombres y apellidos el 99 % de los terceros; la direccion toma el resto del renglon.
 */
describe.each([
  ['Compras Café Seco', dry],
  ['Café Verde', green],
  ['Pasilla', husk],
  ['Facturar Anuncios con Cupos', quota],
  ['Facturar Compras Anunciadas', billing],
  ['Ingresar Compras a Futuro', future],
])('%s: los campos no cortan el texto', (_, content) => {
  const all = fields(content);
  const byKey = (...keys: string[]) => all.filter((f) => keys.includes(f.key));

  it('fechas de al menos 128 px (dd/mm/aaaa con su icono)', () => {
    for (const f of all.filter((f) => f.type === 'date')) {
      expect(px(f.inputWidthClass, 'max-w') ?? 0, f.key).toBeGreaterThanOrEqual(128);
    }
  });

  it('Especial de 332 px (en verde y pasilla es fijo: CV, PASILLA)', () => {
    for (const f of byKey('special').filter((f) => f.value === undefined)) {
      expect(px(f.inputWidthClass, 'min-w'), f.key).toBeGreaterThanOrEqual(332);
    }
  });

  it('nombres de 200 px, apellidos de 220 px y la direccion con el resto del renglon', () => {
    for (const f of byKey('firstName', 'firstNames')) {
      expect(px(f.inputWidthClass, 'min-w'), f.key).toBeGreaterThanOrEqual(200);
    }
    for (const f of byKey('lastName', 'lastNames')) {
      expect(px(f.inputWidthClass, 'min-w'), f.key).toBeGreaterThanOrEqual(220);
    }
    for (const f of byKey('address')) {
      expect(f.rowWidthClass, f.key).toContain('flex-1');
    }
  });
});
