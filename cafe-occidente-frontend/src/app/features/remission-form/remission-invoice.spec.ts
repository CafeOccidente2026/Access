import type { Content } from 'pdfmake/interfaces';
import { describe, expect, it } from 'vitest';

import { RemissionResponse } from '../../core/models/inventory.model';
import { buildRemissionDocDefinition } from './remission-invoice';

describe('buildRemissionDocDefinition', () => {
  const remission: RemissionResponse = {
    id: 1, remissionNumber: 7, agencyId: 1, agencyName: 'Buesaco', remissionDate: '2026-09-29',
    destination: 'ALMACAFE', conductorIdNumber: '1085', conductorName: 'Juan Perez',
    transportCompany: 'TRANS NARIÑO', vehiclePlate: 'ABC123', exported: false, displayNumber: 'SDBU-RP-0007',
    lines: [
      { id: 1, inventoryMovementId: 10, quantity: 500, unitValue: 1255, outputValue: 627500,
        specialType: 'RN', healthyPercentage: 88, fundCode: 'RP', sacos: 9, grossKg: 507.5, exitPercentage: 93.37 },
    ],
  };

  it('prints 4 copies in one PDF and only the first one carries the Factor', () => {
    const content = buildRemissionDocDefinition(remission, null).content as { stack: Content[]; pageBreak?: string }[];
    expect(content).toHaveLength(4);
    expect(content.filter((c) => c.pageBreak === 'before')).toHaveLength(3);
    const withFactor = content.map((c) => JSON.stringify(c.stack).includes('"93,37"'));
    expect(withFactor).toEqual([true, false, false, false]);
    // Fuera del Factor las cuatro copias son iguales.
    const [first, ...rest] = content.map((c) => JSON.stringify(c.stack).replace('"93,37"', '""'));
    rest.forEach((c) => expect(c).toBe(first));
  });

  it('prints "$ Vr_Salida" in the row below each line, under Kilos Netos, without decimals (Remision.txt)', () => {
    const two = { ...remission, lines: [remission.lines[0], { ...remission.lines[0], id: 2, quantity: 280, outputValue: 5373534 }] };
    const copies = buildRemissionDocDefinition(two, null).content as { stack: { table?: { body: { text?: string }[][] } }[] }[];
    const body = copies[0].stack[2].table!.body; // DESPACHOS; fila 0 = titulos
    expect(body[1][4].text).toBe('500,00');
    expect([body[2][3].text, body[2][4].text]).toEqual(['$', '627.500']);
    expect(body[3][4].text).toBe('280,00');
    expect([body[4][3].text, body[4][4].text]).toEqual(['$', '5.373.534']);
    // En todas las copias, no solo en la del Factor.
    expect(JSON.stringify(copies[3].stack)).toContain('"5.373.534"');
  });

  it('prints the Sld3 exit factor, and the origin factor only on lines saved before it existed', () => {
    const text = (exitPercentage: number | null) =>
      JSON.stringify(buildRemissionDocDefinition({ ...remission, lines: [{ ...remission.lines[0], exitPercentage }] }, null).content);
    expect(text(93.37)).toContain('"93,37"');
    expect(text(93.37)).not.toContain('"88,00"');
    expect(text(null)).toContain('"88,00"');
  });

  it('fills the pre-printed boxes with the remission data and marks OTROS for RP', () => {
    const text = JSON.stringify(buildRemissionDocDefinition(remission, null).content);
    for (const value of ['Nº SDBU-RP-0007', '"9"', '507,50','"29"', '"09"', '"2026"', 'ALMACAFE', 'Buesaco', '"RN"', '500,00', 'TRANS NARIÑO', 'ABC123', 'Juan Perez', '1085']) {
      expect(text).toContain(value);
    }
    // Casillero X: fila de LINEA FINANCIAMIENTO (0) u OTROS (2) del bloque de fecha.
    const marks = (fundCode: string) => {
      const doc = buildRemissionDocDefinition({ ...remission, lines: [{ ...remission.lines[0], fundCode }] }, null);
      const first = (doc.content as { stack: { table?: { body: { text?: string }[][] } }[] }[])[0].stack[1];
      return [first.table!.body[0][8].text, first.table!.body[2][8].text];
    };
    expect(marks('LF')).toEqual(['X', '']);
    expect(marks('RP')).toEqual(['', 'X']);
  });
});
