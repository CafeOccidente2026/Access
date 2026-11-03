import { describe, expect, it } from 'vitest';
import writeExcelFile from 'write-excel-file/browser';

import { RemittanceFiles, RemittanceLine } from '../../core/models/remittance.model';
import { remittanceWorkbooks } from './remittance-files';

/** Datos inventados con la estructura de los ejemplos del area (los reales no van a los tests). */
const line = (over: Partial<RemittanceLine>): RemittanceLine => ({
  idNumber: '900100200', sellerName: 'ANA MARIA PRUEBA UNO', purchaseDateTime: '2026-05-16T07:34:20',
  invoice: 'SDXX1001', fundCode: 'LF', specialType: 'NESPRESSO - FTUSA', announcementDate: '2026-05-15',
  netKg: 300, factor: 94, kas: 28200, grossValue: 3000000, unitPrice: 10000, healthyPrice: 12000, bonus: 500,
  healthyPercentage: 73.6, huskPercentage: 8, wastePercentage: 18.4, announcementBasePrice: 2450000,
  bonusTotal: 150000, purchaseBasePrice: 1113500, ...over,
});

const FILES: RemittanceFiles = {
  number: 'SDXX-LF-0007',
  agencyName: 'PUNTO PRUEBA',
  generatedAt: '2026-10-06T09:19:28',
  lines: [
    line({}),
    line({ idNumber: '900100300', sellerName: 'LUIS PRUEBA DOS', invoice: 'SDXX1002', purchaseDateTime: '2026-05-23T00:00:00',
      netKg: 200, factor: 90, kas: 18000, grossValue: 1900000, unitPrice: 9500, bonusTotal: 100000 }),
  ],
};

type Cell = { value?: unknown; type?: unknown; format?: string } | null;

const values = (row: readonly unknown[]) => row.map((c) => (c == null ? null : (c as Cell)!.value));
const byName = (name: string) => {
  const book = remittanceWorkbooks(FILES).find((b) => b.fileName === name);
  expect(book, name).toBeDefined();
  return book!;
};
const utc = (y: number, m: number, d: number, h = 0, mi = 0, s = 0) => new Date(Date.UTC(y, m - 1, d, h, mi, s));

describe('remittanceWorkbooks (RF-12, como los archivos que recibe el area)', () => {
  it('produces the 4 files named after the remittance, each with the sheet of its Access query', () => {
    expect(remittanceWorkbooks(FILES).map((b) => [b.fileName, b.sheet])).toEqual([
      ['ParaOperaciones_SDXX-LF-0007.xlsx', 'ParaOperaciones'],
      ['ParaOpen_SDXX-LF-0007.xlsx', 'ParaOpen'],
      ['SDXX-LF-0007.xlsx', 'GeneraPlano'],
      ['ParaAlmacafe_SDXX-LF-0007.xlsx', 'ParaAlmacafe'],
    ]);
  });

  it('GeneraPlano: the 18 Access columns, PORC_AS = W_AlmSana / Muestra x 100 and the crossed price columns', () => {
    const data = byName('SDXX-LF-0007.xlsx').data;
    expect(values(data[0])).toEqual(['Cedula', 'cantidad', 'ZUND', 'CODFINCA', 'CEDULA_VEND', 'Nombre_Vend', 'Fech',
      'Factura', 'FACTOR', 'VrKgBase', 'PR_BASE_AS', 'SPR_NESPR', 'PORC_AS', 'PORC_AD', 'PR_BASE_TOT', 'VrSprTtl',
      'SPR_NESS_TOT', 'PR_TOTAL']);
    expect(values(data[1])).toEqual([900100200, 300, 'KG', 900100200, 900100200, 'ANA MARIA PRUEBA UNO', '20260515',
      'SDXX1001', 94, 10000, 12000, 500, 73.6, 8, 2450000, 150000, 1113500, 3000000]);
    expect(data).toHaveLength(3);
  });

  it('ParaOpen: Remision first, Porcmerma and fechaOrigEntr = when the remittance was generated', () => {
    const data = byName('ParaOpen_SDXX-LF-0007.xlsx').data;
    expect(values(data[0])).toEqual(['Remision', 'Cedula', 'cantidad', 'ZUND', 'CODFINCA', 'CEDULA_VEND', 'Nombre_Vend',
      'Fech', 'Factura', 'FACTOR', 'VrKgBase', 'PR_BASE_AS', 'SPR_NESPR', 'Porcmerma', 'PORC_AD', 'PR_BASE_TOT',
      'VrSprTtl', 'SPR_NESS_TOT', 'PR_TOTAL', 'fechaOrigEntr']);
    const first = data[1] as Cell[];
    expect(values(first).slice(0, 19)).toEqual(['SDXX-LF-0007', 900100200, 300, 'KG', 900100200, 900100200,
      'ANA MARIA PRUEBA UNO', '20260515', 'SDXX1001', 94, 10000, 12000, 500, 18.4, 8, 2450000, 150000, 1113500, 3000000]);
    expect(first[19]).toEqual({ value: utc(2026, 10, 6, 9, 19, 28), format: 'mm-dd-yy' });
  });

  it('ParaAlmacafe: Cedula, Nombre_Vend, cantidad and Factura (XLSX, as the area receives it)', () => {
    const data = byName('ParaAlmacafe_SDXX-LF-0007.xlsx').data;
    expect(data.map(values)).toEqual([
      ['Cedula', 'Nombre_Vend', 'cantidad', 'Factura'],
      [900100200, 'ANA MARIA PRUEBA UNO', 300, 'SDXX1001'],
      [900100300, 'LUIS PRUEBA DOS', 200, 'SDXX1002'],
    ]);
  });

  it('ParaOperaciones: KAS without /100, the sum row and FACTOR PONDERADO / VR KG PROM REMISION below', () => {
    const data = byName('ParaOperaciones_SDXX-LF-0007.xlsx').data;
    expect(values(data[0])).toEqual(['Cedula', 'Nombre_Vend', 'Agencia', 'Remision', 'Fecha_Compra', 'Factura', 'Fondo',
      'cantidad', 'PORC_AS', 'KAS', 'PR_TOTAL', 'Especial']);
    const first = data[1] as Cell[];
    expect(values(first)).toEqual([900100200, 'ANA MARIA PRUEBA UNO', 'PUNTO PRUEBA', 'SDXX-LF-0007',
      utc(2026, 5, 16, 7, 34, 20), 'SDXX1001', 'LF', 300, 94, 28200, 3000000, 'NESPRESSO - FTUSA']);
    expect(first[4]).toMatchObject({ format: 'mm-dd-yy' });

    // Filas 2 y 3 de Excel: las facturas; 4: sumas; 5 y 6 vacias; 7 y 8: los dos cocientes.
    const sums = data[3] as Cell[];
    expect(sums[7]).toEqual({ value: '=SUM(H2:H3)', type: 'Formula' });
    expect(sums[9]).toEqual({ value: '=SUM(J2:J3)', type: 'Formula' });
    expect(sums[10]).toEqual({ value: '=SUM(K2:K3)', type: 'Formula' });
    expect(values(data[4]).every((v) => v == null)).toBe(true);
    expect(values(data[5]).every((v) => v == null)).toBe(true);
    expect(values(data[6])[4]).toBe('FACTOR PONDERADO');
    expect(data[6][7]).toEqual({ value: '=+J4/H4', type: 'Formula' });
    expect(values(data[7])[4]).toBe('VR KG PROM REMISION');
    expect(data[7][7]).toEqual({ value: '=+K4/H4', type: 'Formula' });
    expect(data).toHaveLength(8);

    // Lo que dan esas formulas con las filas de arriba: sum(KAS)/sum(cantidad) y sum(PR_TOTAL)/sum(cantidad).
    const column = (index: number) => data.slice(1, 3).reduce((sum, row) => sum + Number(values(row)[index]), 0);
    expect(column(7)).toBe(500);
    expect(column(9) / column(7)).toBeCloseTo(92.4, 10);
    expect(column(10) / column(7)).toBe(9800);
  });

  it('a seller id with letters stays as text', () => {
    const files = { ...FILES, lines: [line({ idNumber: 'E-123' })] };
    const almacafe = remittanceWorkbooks(files).find((b) => b.sheet === 'ParaAlmacafe')!.data;
    expect(values(almacafe[1])[0]).toBe('E-123');
  });

  it('builds real xlsx files', async () => {
    for (const book of remittanceWorkbooks(FILES)) {
      const blob = await writeExcelFile(book.data, { sheet: book.sheet }).toBlob();
      expect(new TextDecoder().decode(new Uint8Array(await blob.arrayBuffer()).slice(0, 2))).toBe('PK');
    }
  });
});
