import { strFromU8, unzipSync } from 'fflate';
import { describe, expect, it, vi } from 'vitest';

import { ExportTable, TableExporter, docxBytes, documentXml, sheetData } from './export-table';

/** Demo del 23-nov: una sola tabla para exportar a Excel y a Word, con las columnas y totales del PDF. */
const table: ExportTable = {
  title: 'COMPRAS POR FECHA',
  subtitle: 'Desde 01/11/2026 hasta 23/11/2026',
  columns: ['Factura', 'KN', 'Neto a Pag'],
  rows: [
    { kind: 'group', cells: ['EL TAMBO'] },
    { cells: ['SDTA 43907', { value: 1250.5, format: 'kg' }, { value: 25000000, format: 'money' }] },
    { kind: 'summary', cells: ["Resumir por 'Agencia' = EL TAMBO (1 registro de detalle)"] },
    { kind: 'total', cells: ['Suma total', { value: 1250.5, format: 'kg' }, { value: 25000000, format: 'money' }] },
  ],
};

describe('export-table', () => {
  it('Excel: title, range, bold header, numbers as numbers with their format and bold totals', () => {
    const data = sheetData(table);
    expect(data[0][0]).toMatchObject({ value: 'COMPRAS POR FECHA', fontWeight: 'bold' });
    expect(data[1][0]).toMatchObject({ value: 'Desde 01/11/2026 hasta 23/11/2026' });
    expect(data[3].map((c) => (c as { value?: unknown } | null)?.value)).toEqual(['Factura', 'KN', 'Neto a Pag']);
    expect(data[3][0]).toMatchObject({ fontWeight: 'bold' });
    expect(data[5][1]).toMatchObject({ value: 1250.5, type: Number, format: '#,##0.00' });
    expect(data[5][2]).toMatchObject({ value: 25000000, type: Number, format: '#,##0' });
    expect(data[4][0]).toMatchObject({ value: 'EL TAMBO', fontWeight: 'bold' });
    expect(data[7][0]).toMatchObject({ value: 'Suma total', fontWeight: 'bold' });
    expect(data[7][2]).toMatchObject({ value: 25000000, fontWeight: 'bold' });
  });

  it('Word: a landscape document with the title, the range and one table row per line, numbers like the PDF', () => {
    const xml = documentXml(table);
    expect(xml).toContain('<w:t xml:space="preserve">COMPRAS POR FECHA</w:t>');
    expect(xml).toContain('Desde 01/11/2026 hasta 23/11/2026');
    expect(xml).toContain('w:orient="landscape"');
    expect(xml.match(/<w:tr>/g)).toHaveLength(5);
    expect(xml).toContain('>1.250,50<');
    expect(xml).toContain('>25.000.000<');
    expect(xml).toContain('Resumir por &apos;Agencia&apos; = EL TAMBO');
  });

  it('Word: the .docx is a zip with the parts Word needs', () => {
    const files = unzipSync(docxBytes(table));
    expect(Object.keys(files).sort()).toEqual(['[Content_Types].xml', '_rels/.rels', 'word/document.xml']);
    expect(strFromU8(files['word/document.xml'])).toContain('COMPRAS POR FECHA');
  });

  it('Word: Exportar a Word downloads the .docx with the file name', () => {
    const created = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:prueba');
    const clicked = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);
    let name = '';
    clicked.mockImplementation(function (this: HTMLAnchorElement) {
      name = this.download;
    });

    new TableExporter().docx(table, 'relacion cheques');

    expect(created).toHaveBeenCalledWith(expect.any(Blob));
    expect(name).toBe('relacion cheques.docx');
    created.mockRestore();
    clicked.mockRestore();
  });
});
