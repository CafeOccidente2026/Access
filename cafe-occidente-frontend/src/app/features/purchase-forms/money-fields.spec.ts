import { describe, expect, it } from 'vitest';

import dry from '../../../../public/assets/data/purchase-form-dry.json';
import green from '../../../../public/assets/data/purchase-form-green.json';
import husk from '../../../../public/assets/data/purchase-form-husk.json';
import quotaBilling from '../../../../public/assets/data/purchase-form-quota-billing.json';
import quotaPurchase from '../../../../public/assets/data/purchase-form-quota-purchase.json';

/** Hallazgo del 18-nov: la plata que digita el cajero se ve con puntos de miles mientras se escribe. */
const TYPED_MONEY = ['compKgPrice', 'penalty', 'shrinkageDiscount', 'otherDiscounts'];

function fields(content: object): { key: string; liveThousands?: boolean }[] {
  return Object.values(content).flatMap((v) => (Array.isArray(v) ? v : v && typeof v === 'object' && 'key' in v ? [v] : []));
}

describe('typed money in the purchase screens', () => {
  for (const [name, content] of Object.entries({ dry, green, husk, quotaBilling, quotaPurchase })) {
    it(`${name}: every typed money field shows the thousands while typing`, () => {
      const money = fields(content).filter((f) => TYPED_MONEY.includes(f.key));
      expect(money.length).toBeGreaterThan(0);
      for (const f of money) {
        expect(f, `${name}.${f.key}`).toMatchObject({ liveThousands: true });
      }
    });
  }

  it('Café Verde has Vr. Kilo Comp, Castigo, Descuento Fro and Otros Dscts', () => {
    expect(fields(green).filter((f) => TYPED_MONEY.includes(f.key)).map((f) => f.key).sort())
      .toEqual(['compKgPrice', 'otherDiscounts', 'penalty', 'shrinkageDiscount']);
  });
});
