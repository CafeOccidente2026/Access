import { describe, expect, it } from 'vitest';

import { buildReprintDoc } from './purchase-reprint';
import { DRY, GREEN, HUSK, detail } from './purchase-query.testing';

const text = (d: ReturnType<typeof buildReprintDoc>) => JSON.stringify(d.content);

describe('buildReprintDoc (RN-26.6 Reimprimir Dcto Equiv)', () => {
  it('reprints the dry coffee invoice with the saved payment methods', () => {
    const doc = buildReprintDoc(detail(), null, 'ANULADA', '#b91c1c');
    expect(text(doc)).toContain('SDBU - 27.869');
    expect(text(doc)).toContain('11.655.579');
    expect(text(doc)).toContain('300.000');
    expect(doc.watermark).toBeUndefined();
  });

  it('reprints the green coffee invoice ("Factura verdes") and the husk invoice', () => {
    expect(text(buildReprintDoc(detail({ module: 'GREEN', purchase: GREEN }), null, 'ANULADA', undefined))).toContain('"Kilos Verdes"');
    expect(text(buildReprintDoc(detail({ module: 'HUSK', purchase: HUSK }), null, 'ANULADA', undefined))).toContain('Kilos Netos Pasilla');
  });

  it('without a saved split the whole net goes to its payment method, like before', () => {
    const doc = buildReprintDoc(detail({ payment: null, purchase: { ...DRY, paymentMethod: 'EFECTIVO' } }), null, 'ANULADA', undefined);
    expect(text(doc)).toContain('12.255.579');
  });

  it('an annulled purchase carries a big ANULADA mark on every page', () => {
    const doc = buildReprintDoc(detail({ status: 'ANULADA' }), null, 'ANULADA', '#b91c1c');
    expect(doc.watermark).toMatchObject({ text: 'ANULADA', color: '#b91c1c', bold: true });
  });
});
