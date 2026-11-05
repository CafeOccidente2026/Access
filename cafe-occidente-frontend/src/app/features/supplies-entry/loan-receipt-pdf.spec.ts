import { describe, expect, it } from 'vitest';

import content from '../../../../public/assets/data/supplies-entry.json';
import { buildLoanReceiptDoc } from './loan-receipt-pdf';
import { SuppliesEntryContent } from './supplies-entry.model';

describe('loan receipt (Prest Empaques, D7)', () => {
  it('prints two copies with the name, cédula, quantity and type of packaging', () => {
    const doc = buildLoanReceiptDoc((content as SuppliesEntryContent).loanReceipt, {
      id: 1, transactionId: 'SDTA5PRE001', agencyName: 'El Tambo', entryDate: '2026-11-05', idNumber: '87304051',
      firstNames: 'ANA MARIA', lastNames: 'PRUEBA LOPEZ',
    }, 12, 'USADO');

    const copies = doc.content as { stack: unknown[]; pageBreak?: string }[];
    expect(copies).toHaveLength(2);
    expect(copies[0].pageBreak).toBe('after');
    for (const copy of copies) {
      const text = JSON.stringify(copy.stack);
      expect(text).toContain('ANA MARIA PRUEBA LOPEZ');
      expect(text).toContain('87.304.051');
      expect(text).toContain('devolver la cantidad de 12 Empaques USADO');
      expect(text).toContain('El Tambo, 5 de noviembre de 2026');
    }
  });
});
