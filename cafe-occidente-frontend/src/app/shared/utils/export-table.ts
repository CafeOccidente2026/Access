import { Injectable } from '@angular/core';
import { strToU8, zipSync } from 'fflate';
import type { Cell, SheetData } from 'write-excel-file/browser';

import { formatKilos, formatMoney } from './number-format';

/** Un numero de la tabla: en Excel va como numero con su formato; en Word, escrito como en el PDF. */
export interface ExportNumber {
  readonly value: number;
  readonly format: 'money' | 'kg';
}

export type ExportCell = string | ExportNumber | null;

/**
 * Una fila: el detalle (kind vacio), el titulo de un grupo ("EL TAMBO"), su resumen ("Resumir por...") o una suma.
 * Las filas de grupo y resumen traen un solo texto, que ocupa toda la fila.
 */
export interface ExportRow {
  readonly kind?: 'group' | 'summary' | 'total';
  readonly cells: readonly ExportCell[];
}

/** La tabla de un informe, la misma para Excel y para Word (Compras por Fecha, Relacion Cheques). */
export interface ExportTable {
  readonly title: string;
  readonly subtitle?: string;
  readonly columns: readonly string[];
  readonly rows: readonly ExportRow[];
}

const EXCEL_FORMAT: Record<ExportNumber['format'], string> = { money: '#,##0', kg: '#,##0.00' };
const shown = (n: ExportNumber): string => (n.format === 'money' ? formatMoney(n.value) : formatKilos(n.value));
const bold = (row: ExportRow) => row.kind === 'group' || row.kind === 'total';

/** Demo del 23-nov: la hoja de Excel (titulo, rango, encabezado en negrilla, numeros como numeros y sumas en negrilla). */
export function sheetData(table: ExportTable): SheetData {
  const text = (value: string, strong = false): Cell => ({ value, ...(strong ? { fontWeight: 'bold' as const } : {}) });
  const cell = (c: ExportCell, strong: boolean): Cell => {
    if (c == null || typeof c === 'string') {
      return c ? text(c, strong) : null;
    }
    return { value: c.value, type: Number, format: EXCEL_FORMAT[c.format], ...(strong ? { fontWeight: 'bold' as const } : {}) };
  };
  return [
    [text(table.title, true)],
    [table.subtitle ? text(table.subtitle) : null],
    [null],
    table.columns.map((c) => text(c, true)),
    ...table.rows.map((row) => row.cells.map((c) => cell(c, bold(row)))),
  ];
}

const escape = (s: string) =>
  s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&apos;');

function run(text: string, strong = false, size?: number): string {
  const props = `${strong ? '<w:b/>' : ''}${size ? `<w:sz w:val="${size}"/>` : ''}`;
  return `<w:r>${props ? `<w:rPr>${props}</w:rPr>` : ''}<w:t xml:space="preserve">${escape(text)}</w:t></w:r>`;
}

function tableCell(c: ExportCell, strong: boolean, span = 1): string {
  const right = c != null && typeof c !== 'string';
  const value = c == null ? '' : typeof c === 'string' ? c : shown(c);
  const props = `${span > 1 ? `<w:gridSpan w:val="${span}"/>` : ''}`;
  return `<w:tc><w:tcPr>${props}</w:tcPr><w:p><w:pPr>${right ? '<w:jc w:val="right"/>' : ''}<w:spacing w:after="0"/></w:pPr>`
    + `${run(value, strong, 16)}</w:p></w:tc>`;
}

/** Demo del 23-nov: el cuerpo del documento de Word, horizontal, con la tabla del informe. */
export function documentXml(table: ExportTable): string {
  const width = table.columns.length;
  const header = `<w:tr>${table.columns.map((c) => tableCell(c, true)).join('')}</w:tr>`;
  const rows = table.rows.map((row) =>
    row.kind === 'group' || row.kind === 'summary'
      ? `<w:tr>${tableCell(row.cells[0], row.kind === 'group', width)}</w:tr>`
      : `<w:tr>${Array.from({ length: width }, (_, i) => tableCell(row.cells[i] ?? null, bold(row))).join('')}</w:tr>`);
  const border = (side: string) => `<w:${side} w:val="single" w:sz="4" w:space="0" w:color="999999"/>`;
  return '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
    + '<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>'
    + `<w:p>${run(table.title, true, 28)}</w:p>`
    + (table.subtitle ? `<w:p>${run(table.subtitle, false, 18)}</w:p>` : '')
    + `<w:tbl><w:tblPr><w:tblW w:w="5000" w:type="pct"/><w:tblBorders>${['top', 'bottom', 'insideH'].map(border).join('')}`
    + `</w:tblBorders></w:tblPr>${header}${rows.join('')}</w:tbl>`
    + '<w:sectPr><w:pgSz w:w="15840" w:h="12240" w:orient="landscape"/>'
    + '<w:pgMar w:top="720" w:right="720" w:bottom="720" w:left="720" w:header="0" w:footer="0" w:gutter="0"/></w:sectPr>'
    + '</w:body></w:document>';
}

const CONTENT_TYPES = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
  + '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">'
  + '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>'
  + '<Default Extension="xml" ContentType="application/xml"/>'
  + '<Override PartName="/word/document.xml"'
  + ' ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>';
const RELS = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
  + '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
  + '<Relationship Id="rId1" Target="word/document.xml"'
  + ' Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument"/></Relationships>';

/** El .docx: un zip con las tres partes minimas que Word necesita. */
export function docxBytes(table: ExportTable): Uint8Array {
  return zipSync({
    '[Content_Types].xml': strToU8(CONTENT_TYPES),
    '_rels/.rels': strToU8(RELS),
    'word/document.xml': strToU8(documentXml(table)),
  });
}

function save(blob: Blob, fileName: string): void {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  link.click();
  setTimeout(() => URL.revokeObjectURL(url));
}

/** Descarga la tabla en Excel; la libreria se carga solo al exportar. */
export async function downloadXlsx(table: ExportTable, fileName: string): Promise<void> {
  const { default: writeExcelFile } = await import('write-excel-file/browser');
  await writeExcelFile(sheetData(table)).toFile(`${fileName}.xlsx`);
}

/** Descarga la tabla en Word (.docx). */
export function downloadDocx(table: ExportTable, fileName: string): void {
  save(new Blob([docxBytes(table) as BlobPart], {
    type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  }), `${fileName}.docx`);
}

/** Lo que usan las pantallas para exportar (inyectable, asi las pruebas no descargan archivos). */
@Injectable({ providedIn: 'root' })
export class TableExporter {
  xlsx(table: ExportTable, fileName: string): Promise<void> {
    return downloadXlsx(table, fileName);
  }

  docx(table: ExportTable, fileName: string): void {
    downloadDocx(table, fileName);
  }
}
