import type { SheetData } from 'write-excel-file/browser';

import { RemittanceFiles, RemittanceLine } from '../../core/models/remittance.model';

/**
 * RF-12: los 4 archivos de la remesa, iguales a los que recibe hoy el area (consultas GeneraPlano,
 * ParaOperaciones, ParaOpen y ParaAlmacafe de Access, exportadas con TransferSpreadsheet). Nombres de
 * archivo, de hoja y de columna son el formato de intercambio: no se traducen ni se cambian. Los valores ya
 * vienen calculados del servidor; aca solo se ordenan y se les da el tipo de celda de los ejemplos.
 */
export interface RemittanceWorkbook {
  readonly fileName: string;
  readonly sheet: string;
  readonly data: SheetData;
}

type Cell = SheetData[number][number];
type Column = readonly [header: string, value: (l: RemittanceLine, f: RemittanceFiles) => Cell];

const FECHA = 'mm-dd-yy';

/** Celda simple; vacia si no hay valor. */
const cell = (value: string | number | null | undefined): Cell => (value == null || value === '' ? null : { value });

/** Fecha y hora sin zona ("2026-05-16T07:34:20") tal cual, en el formato de Access. */
function dateTime(iso: string): Cell {
  const [date, time = '00:00:00'] = iso.split('T');
  const [y, m, d] = date.split('-').map(Number);
  const [h, mi, s] = time.split(':').map((part) => Math.trunc(Number(part)));
  return { value: new Date(Date.UTC(y, m - 1, d, h, mi, s || 0)), format: FECHA };
}

/** La cedula va como numero, como en los ejemplos; si trae letras, como texto. */
const idNumber = (l: RemittanceLine): Cell => cell(/^\d+$/.test(l.idNumber) ? Number(l.idNumber) : l.idNumber);
/** Fech: fecha del anuncio AAAAMMDD, como texto. */
const fech = (l: RemittanceLine): Cell => cell(l.announcementDate?.replaceAll('-', ''));

const CEDULA: Column = ['Cedula', idNumber];
const CANTIDAD: Column = ['cantidad', (l) => cell(l.netKg)];
const ZUND: Column = ['ZUND', () => cell('KG')];
const CODFINCA: Column = ['CODFINCA', idNumber];
const CEDULA_VEND: Column = ['CEDULA_VEND', idNumber];
const NOMBRE_VEND: Column = ['Nombre_Vend', (l) => cell(l.sellerName)];
const FECH: Column = ['Fech', fech];
const FACTURA: Column = ['Factura', (l) => cell(l.invoice)];
const FACTOR: Column = ['FACTOR', (l) => cell(l.factor)];
const VR_KG_BASE: Column = ['VrKgBase', (l) => cell(l.unitPrice)];
const PR_BASE_AS: Column = ['PR_BASE_AS', (l) => cell(l.healthyPrice)];
const SPR_NESPR: Column = ['SPR_NESPR', (l) => cell(l.bonus)];
const PORC_AD: Column = ['PORC_AD', (l) => cell(l.huskPercentage)];
const PR_BASE_TOT: Column = ['PR_BASE_TOT', (l) => cell(l.announcementBasePrice)];
const VR_SPR_TTL: Column = ['VrSprTtl', (l) => cell(l.bonusTotal)];
const SPR_NESS_TOT: Column = ['SPR_NESS_TOT', (l) => cell(l.purchaseBasePrice)];
const PR_TOTAL: Column = ['PR_TOTAL', (l) => cell(l.grossValue)];

const GENERA_PLANO: readonly Column[] = [CEDULA, CANTIDAD, ZUND, CODFINCA, CEDULA_VEND, NOMBRE_VEND, FECH, FACTURA,
  FACTOR, VR_KG_BASE, PR_BASE_AS, SPR_NESPR, ['PORC_AS', (l) => cell(l.healthyPercentage)], PORC_AD, PR_BASE_TOT,
  VR_SPR_TTL, SPR_NESS_TOT, PR_TOTAL];

const PARA_OPEN: readonly Column[] = [['Remision', (_, f) => cell(f.number)], CEDULA, CANTIDAD, ZUND, CODFINCA,
  CEDULA_VEND, NOMBRE_VEND, FECH, FACTURA, FACTOR, VR_KG_BASE, PR_BASE_AS, SPR_NESPR,
  ['Porcmerma', (l) => cell(l.wastePercentage)], PORC_AD, PR_BASE_TOT, VR_SPR_TTL, SPR_NESS_TOT, PR_TOTAL,
  ['fechaOrigEntr', (_, f) => dateTime(f.generatedAt)]];

const PARA_ALMACAFE: readonly Column[] = [CEDULA, NOMBRE_VEND, CANTIDAD, FACTURA];

/** ParaOperaciones: PORC_AS es el factor y KAS = cantidad x PORC_AS, sin dividir por 100, como Access. */
const PARA_OPERACIONES: readonly Column[] = [CEDULA, NOMBRE_VEND, ['Agencia', (_, f) => cell(f.agencyName)],
  ['Remision', (_, f) => cell(f.number)], ['Fecha_Compra', (l) => dateTime(l.purchaseDateTime)], FACTURA,
  ['Fondo', (l) => cell(l.fundCode)], CANTIDAD, ['PORC_AS', (l) => cell(l.factor)], ['KAS', (l) => cell(l.kas)],
  PR_TOTAL, ['Especial', (l) => cell(l.specialType)]];

function table(columns: readonly Column[], files: RemittanceFiles): Cell[][] {
  return [columns.map(([header]) => cell(header)), ...files.lines.map((l) => columns.map(([, value]) => value(l, files)))];
}

const formula = (value: string): Cell => ({ value, type: 'Formula' });

/**
 * RN-12.5: debajo de ParaOperaciones, como el archivo del area, la fila con las sumas de cantidad (H), KAS (J)
 * y PR_TOTAL (K), dos filas vacias, y FACTOR PONDERADO = suma KAS / suma cantidad y VR KG PROM REMISION =
 * suma PR_TOTAL / suma cantidad (etiqueta en Fecha_Compra, valor en cantidad).
 */
function operationsTotals(lastLineRow: number): Cell[][] {
  const width = PARA_OPERACIONES.length;
  const row = (cells: Record<number, Cell>) => Array.from({ length: width }, (_, i) => cells[i] ?? null);
  const sumRow = lastLineRow + 1;
  return [
    row({ 7: formula(`=SUM(H2:H${lastLineRow})`), 9: formula(`=SUM(J2:J${lastLineRow})`), 10: formula(`=SUM(K2:K${lastLineRow})`) }),
    row({}),
    row({}),
    row({ 4: cell('FACTOR PONDERADO'), 7: formula(`=+J${sumRow}/H${sumRow}`) }),
    row({ 4: cell('VR KG PROM REMISION'), 7: formula(`=+K${sumRow}/H${sumRow}`) }),
  ];
}

export function remittanceWorkbooks(files: RemittanceFiles): RemittanceWorkbook[] {
  const n = files.number;
  return [
    { fileName: `ParaOperaciones_${n}.xlsx`, sheet: 'ParaOperaciones',
      data: [...table(PARA_OPERACIONES, files), ...operationsTotals(files.lines.length + 1)] },
    { fileName: `ParaOpen_${n}.xlsx`, sheet: 'ParaOpen', data: table(PARA_OPEN, files) },
    { fileName: `${n}.xlsx`, sheet: 'GeneraPlano', data: table(GENERA_PLANO, files) },
    { fileName: `ParaAlmacafe_${n}.xlsx`, sheet: 'ParaAlmacafe', data: table(PARA_ALMACAFE, files) },
  ];
}

/** Descarga los libros uno tras otro (el navegador puede pedir permiso para varias descargas). */
export async function saveWorkbooks(books: readonly RemittanceWorkbook[]): Promise<void> {
  const { default: writeExcelFile } = await import('write-excel-file/browser');
  for (const book of books) {
    await writeExcelFile(book.data, { sheet: book.sheet }).toFile(book.fileName);
  }
}
