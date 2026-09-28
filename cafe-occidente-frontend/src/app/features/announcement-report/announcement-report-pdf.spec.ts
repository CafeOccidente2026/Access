import { describe, expect, it } from 'vitest';

import { DryCoffeePurchaseResponse } from '../../core/models/dry-coffee-purchase.model';
import {
  buildAnnouncementDetailDoc,
  buildAnnouncementSummaryDoc,
  groupByAnnouncement,
  shortDate,
  weightedFactor,
} from './announcement-report-pdf';

function purchase(over: Partial<DryCoffeePurchaseResponse>): DryCoffeePurchaseResponse {
  return {
    invoiceNumber: 1, purchaseDate: '2026-05-09', agencyName: 'EL TAMBO', fundCode: 'LF', specialType: 'NESPRESSO',
    productCode: '0110001000007', announcementNumber: 'SDTA-3443', idNumber: '27286462', firstName: 'MARIA',
    lastName: 'ESPAÑA', netKg: 100, healthyPercentage: 90, unitPrice: 19115, grossValue: 1911500,
    associateContribution: 0, cooperativeDiscount: 0, freightDiscount: 0, withholding: 0, otherDiscounts: 0,
    netToPay: 1911500, paymentMethod: 'EFECTIVO', ...over,
  } as DryCoffeePurchaseResponse;
}

describe('announcement report', () => {
  it('makes one block per announcement, groups by Cod_Prod and orders by invoice', () => {
    const blocks = groupByAnnouncement([
      purchase({ invoiceNumber: 3, productCode: 'B' }),
      purchase({ invoiceNumber: 1, productCode: 'A' }),
      purchase({ invoiceNumber: 2, announcementNumber: 'SDTA-3444', specialType: 'RN' }),
      purchase({ invoiceNumber: 4, productCode: 'A' }),
    ]);

    expect(blocks.map((b) => `${b.announcementNumber} ${b.specialType}`)).toEqual(['3443 NESPRESSO', '3444 RN']);
    expect(blocks[0].groups.map((g) => [g.productCode, g.rows.map((p) => p.invoiceNumber)])).toEqual([
      ['A', [1, 4]],
      ['B', [3]],
    ]);
  });

  it('weights the factor by KN and returns 0 instead of Access "#¡Núm!" when KN is 0', () => {
    expect(weightedFactor([purchase({ netKg: 100, healthyPercentage: 90 }), purchase({ netKg: 300, healthyPercentage: 94 })])).toBe(93);
    expect(weightedFactor([purchase({ netKg: 0 })])).toBe(0);
  });

  it('formats the date like Access Short Date', () => {
    expect(shortDate('2026-05-09')).toBe('9/05/2026');
  });

  it('breaks the page between announcement blocks in both reports', () => {
    const purchases = [purchase({}), purchase({ invoiceNumber: 2, announcementNumber: 'SDTA-3444' })];
    for (const doc of [buildAnnouncementDetailDoc(purchases, '9/05/2026'), buildAnnouncementSummaryDoc(purchases, '9/05/2026')]) {
      const content = doc.content as { pageBreak?: string }[];
      expect(doc.pageOrientation).toBe('landscape');
      expect(content.filter((c) => c.pageBreak === 'before')).toHaveLength(1);
    }
  });
});
