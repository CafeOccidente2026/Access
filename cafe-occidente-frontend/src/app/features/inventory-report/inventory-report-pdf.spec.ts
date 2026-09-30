import pdfMake from 'pdfmake/build/pdfmake';
import pdfFonts from 'pdfmake/build/vfs_fonts';
import { describe, expect, it } from 'vitest';

import { InventoryReportRow } from '../../core/models/inventory.model';
import { buildInventoryReportDoc, withBalances } from './inventory-report-pdf';
import { specialOptions } from './inventory-report';

const entry = (netKg: number, pct: number, value: number): InventoryReportRow => ({
  date: '2026-01-17', invoiceNumber: 43000, remissionNumber: null, productCode: '0110002000002', specialType: 'RN',
  netKg, healthyPercentage: pct, inventoryValue: value, quantity: null, exitPercentage: null, outputValue: null,
});
const exit = (qty: number, pct: number, value: number): InventoryReportRow => ({
  date: '2026-01-29', invoiceNumber: null, remissionNumber: 'SDBU-RP-0436', productCode: '0110002000002', specialType: 'RN',
  netKg: null, healthyPercentage: null, inventoryValue: null, quantity: qty, exitPercentage: pct, outputValue: value,
});

describe('inventory report', () => {
  it('runs the balances with the INVENTARIO.txt formulas', () => {
    const rows = withBalances([entry(30, 90, 600000), entry(10, 94, 220000), exit(20, 91, 410000)]);
    // Saldo kilos 30 -> 40 -> 20 ; saldo valor 600000 -> 820000 -> 410000 ; Vr unit = valor / kilos
    expect(rows.map((r) => r.balanceKg)).toEqual([30, 40, 20]);
    expect(rows.map((r) => r.balanceValue)).toEqual([600000, 820000, 410000]);
    expect(rows[2].unitValue).toBe(20500);
    // % pond = (90*30 + 94*10 - 20*91) / 20 = (2700 + 940 - 1820) / 20 = 91
    expect(rows[1].weightedPercentage).toBe(91);
    expect(rows[2].weightedPercentage).toBe(91);
  });

  it('shows 0 instead of Access "#¡Div/0!" when the balance is empty', () => {
    const [, emptied] = withBalances([entry(10, 90, 100000), exit(10, 90, 100000)]);
    expect(emptied.unitValue).toBe(0);
    expect(emptied.weightedPercentage).toBe(0);
  });

  it('is one continuous page by code and paginated letter landscape by specialty', () => {
    const rows = [entry(30, 90, 600000)];
    expect(buildInventoryReportDoc(rows, true).pageSize).toEqual({ width: 792, height: 'auto' });
    const paged = buildInventoryReportDoc(rows, false);
    expect(paged.pageSize).toBe('LETTER');
    expect(paged.pageOrientation).toBe('landscape');
    expect(JSON.stringify(paged.content)).toContain('"600.000,00"');
  });

  it('really renders both variants (auto height cannot carry a page footer)', async () => {
    pdfMake.vfs = pdfFonts;
    const rows = [entry(30, 90, 600000), exit(20, 91, 410000)];
    for (const continuous of [true, false]) {
      const size = await new Promise<number>((resolve, reject) => {
        try {
          pdfMake.createPdf(buildInventoryReportDoc(rows, continuous)).getBuffer((b: Uint8Array) => resolve(b.length));
        } catch (e) {
          reject(e);
        }
      });
      expect(size).toBeGreaterThan(1000);
    }
  });

  it('builds the specialty list from the purchase forms plus Verde and Pasilla', () => {
    const forms = [{ fields: [{ key: 'special', options: ['RN', 'NESPRESSO - FTUSA'] }] }, { q: [{ key: 'special', options: ['RN'] }] }];
    expect(specialOptions(forms)).toEqual(['CV', 'NESPRESSO - FTUSA', 'PASILLA', 'RN']);
  });
});
