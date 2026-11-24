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
  /** Genera Plano: texto separado por comas en vez de Excel. */
  readonly csv?: boolean;
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

/** Sin '=' inicial: write-excel-file escribe el texto tal cual en <f>, como el archivo del area. */
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
    row({ 7: formula(`SUM(H2:H${lastLineRow})`), 9: formula(`SUM(J2:J${lastLineRow})`), 10: formula(`SUM(K2:K${lastLineRow})`) }),
    row({}),
    row({}),
    row({ 4: cell('FACTOR PONDERADO'), 7: formula(`+J${sumRow}/H${sumRow}`) }),
    row({ 4: cell('VR KG PROM REMISION'), 7: formula(`+K${sumRow}/H${sumRow}`) }),
  ];
}

/**
 * Nombres del 24-nov: el archivo + la remision + el tipo de cafe, pegados (ParaOperacionesSDTA-970SECO.xlsx). Genera
 * Plano va en CSV con solo el numero de la remision (SDTA-970.csv), sin encabezados.
 */
export function remittanceWorkbooks(files: RemittanceFiles): RemittanceWorkbook[] {
  const suffix = files.number + files.coffeeType;
  return [
    { fileName: `ParaOperaciones${suffix}.xlsx`, sheet: 'ParaOperaciones',
      data: [...table(PARA_OPERACIONES, files), ...operationsTotals(files.lines.length + 1)] },
    { fileName: `ParaOpen${suffix}.xlsx`, sheet: 'ParaOpen', data: table(PARA_OPEN, files) },
    { fileName: `${files.number}.csv`, sheet: 'GeneraPlano', data: table(GENERA_PLANO, files).slice(1), csv: true },
    { fileName: `ParaAlmacafe${suffix}.xlsx`, sheet: 'ParaAlmacafe', data: table(PARA_ALMACAFE, files) },
  ];
}

/**
 * Windows-1252 (ANSI), como exporta Access: asi la Ñ y las tildes van en un byte. De 0xA0 a 0xFF es igual a
 * Latin-1; de 0x80 a 0x9F, estos caracteres (en ese orden; 0 = sin caracter). Lo demas sale como '?'.
 */
const CP1252_HIGH = [
  0x20ac, 0, 0x201a, 0x0192, 0x201e, 0x2026, 0x2020, 0x2021, 0x02c6, 0x2030, 0x0160, 0x2039, 0x0152, 0, 0x017d, 0,
  0, 0x2018, 0x2019, 0x201c, 0x201d, 0x2022, 0x2013, 0x2014, 0x02dc, 0x2122, 0x0161, 0x203a, 0x0153, 0, 0x017e, 0x0178,
];

function windows1252(text: string): Uint8Array<ArrayBuffer> {
  return new Uint8Array(Array.from(text, (char) => {
    const code = char.codePointAt(0) ?? 0;
    if (code < 0x80 || (code >= 0xa0 && code <= 0xff)) {
      return code;
    }
    const high = CP1252_HIGH.indexOf(code);
    return high >= 0 ? 0x80 + high : 0x3f;
  }));
}

/** Un campo del CSV: numeros con todos sus decimales y sin miles; un texto con coma o comillas, entre comillas. */
function csvField(cell: Cell): string {
  const value = cell == null ? '' : String((cell as { value: unknown }).value);
  return /[",\r\n]/.test(value) ? `"${value.replaceAll('"', '""')}"` : value;
}

/** Genera Plano: una linea por factura, separada por comas y terminada en CRLF, sin encabezados. */
function csvBlob(data: SheetData): Blob {
  const text = (data as Cell[][]).map((row) => row.map(csvField).join(',') + '\r\n').join('');
  return new Blob([windows1252(text)], { type: 'text/csv' });
}

/** La fuente con la que exporta Access (MS Sans Serif 11), igual que los archivos de referencia. */
const ACCESS_FONT = { fontFamily: 'MS Sans Serif', fontSize: 11 } as const;

/** Arma los .xlsx antes del clic: asi la descarga sale del clic mismo y Chrome no la bloquea. */
export async function workbookBlobs(books: readonly RemittanceWorkbook[]): Promise<Map<string, Blob>> {
  const { default: writeExcelFile } = await import('write-excel-file/browser');
  const blobs = new Map<string, Blob>();
  for (const book of books) {
    blobs.set(book.fileName, book.csv ? csvBlob(book.data)
      : await writeExcelFile(book.data, { sheet: book.sheet }, ACCESS_FONT).toBlob());
  }
  return blobs;
}

/** Descarga un archivo ya armado, sin esperar nada (tiene que correr dentro del clic). */
export function saveBlob(fileName: string, blob: Blob): void {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  link.click();
  setTimeout(() => URL.revokeObjectURL(url));
}
